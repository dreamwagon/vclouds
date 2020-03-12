#import "MatDefs/Common/Compat.glsllib"

uniform sampler3D m_CloudShapeNoise;
uniform sampler3D m_CloudErosionNoise;
uniform sampler2D m_WeatherMap;
uniform sampler2D m_Curl;

//Sky
uniform sampler2D m_SkyColor;
uniform vec3 m_SunColor;
uniform vec3 m_AmbientColor;

//Weather
uniform float m_WindSpeed;
uniform float m_WeatherMapWindFactor;
uniform vec3 m_WindDirection;
uniform vec3 m_WindTurbulanceFactor;

//Cloud Modifiers
uniform float m_CloudCoverage;
uniform float m_HorizonViewThreshold;
uniform int m_AtmosphereLevels;
uniform float[6] m_AtmosphereLevelAltitudes;
uniform float[6] m_AtmosphereLevelHeights;
uniform bool m_DetailedEdges;
uniform float m_DetailedEdgeThreshold;

uniform vec3 m_CameraDir;
uniform vec3 m_LightDir;

uniform float m_PlanetRadius;
uniform float m_SkyRadius;
uniform float m_CloudsFrom;
uniform float m_CloudsTo;

//The number of steps
uniform int m_RayMarchSteps;

uniform vec3 g_CameraPosition;
uniform vec3 g_CameraDirection;
uniform float g_Time; //total time

in vec3 pos;
in vec2 fpPos;

varying float time;
varying vec3 norm;
varying vec2 texCoord;

//TODO change to params?
const float SunScale = 0.1;
const vec3 SunIrradiance = vec3(1);

// Cone sampling random offsets
uniform vec3 noiseKernel[6u] = vec3[]
(
	vec3( 0.38051305,  0.92453449, -0.02111345),
	vec3(-0.50625799, -0.03590792, -0.86163418),
	vec3(-0.32509218, -0.94557439,  0.01428793),
	vec3( 0.09026238, -0.27376545,  0.95755165),
	vec3( 0.28128598,  0.42443639, -0.86065785),
	vec3(-0.16852403,  0.14748697,  0.97460106)
);

#define BAYER_FACTOR 1.0/16.0
uniform float bayerFilter[16u] = float[]
(
	0.0*BAYER_FACTOR, 8.0*BAYER_FACTOR, 2.0*BAYER_FACTOR, 10.0*BAYER_FACTOR,
	12.0*BAYER_FACTOR, 4.0*BAYER_FACTOR, 14.0*BAYER_FACTOR, 6.0*BAYER_FACTOR,
	3.0*BAYER_FACTOR, 11.0*BAYER_FACTOR, 1.0*BAYER_FACTOR, 9.0*BAYER_FACTOR,
	15.0*BAYER_FACTOR, 7.0*BAYER_FACTOR, 13.0*BAYER_FACTOR, 5.0*BAYER_FACTOR
);

float HG(float costheta, float g) {

	return ((1.0-g*g)/pow((1.0+g*g-2.0*g*costheta), 3.0/2.0)) /4.0 * 3.1415;
}

float intersectSphere(const vec3 pos, const vec3 dir, const float r) {
    float a = 2.0 * dot(dir, dir);
    float b = 2.0 * dot(dir, pos);
    float c = dot(pos, pos);
	float d = (b*b - 2.0*a*(c - r * r));
    return max(0.0, (-b+sqrt(d))/a);
}

//Returns the cloud layer scaler and altitude level based on ray altitude
vec2 getCloudLayerAndHeight(vec3 p, vec2 heightAndAlt){
	vec2 layerAndHeight = vec2(0.0, 1.0);
	for( int h=0; h< m_AtmosphereLevels; h++){
		if (heightAndAlt.y >= m_AtmosphereLevelAltitudes[h]){
			layerAndHeight.x = m_AtmosphereLevelAltitudes[h];
			layerAndHeight.y = m_AtmosphereLevelHeights[h];
		}
	}
	return layerAndHeight;
}

float getHeightFractionForPoint(vec3 p, vec2 heightAndAlt){

	float cloudHeightRange = (m_CloudsTo - m_CloudsFrom);

	vec2 cloudLayerAndHeight = getCloudLayerAndHeight(p, heightAndAlt);

	float cloudLayer = cloudLayerAndHeight.x;

	//The total height of the cloud area times the height signal
	float height = cloudLayerAndHeight.y  * (cloudHeightRange/m_AtmosphereLevels) * heightAndAlt.x;

	//The current altitude of the ray position modified by the altitude signal
	float altitude = p.y;
	float altitudeStart = m_CloudsFrom + (cloudHeightRange * cloudLayer);

	float heightScalar = 1.0/height;
	//float altitudeDiff = altitude - altitudeStart;
	float altitudeDiff = p.y - altitudeStart;
	float heightSignal = altitudeDiff * (altitudeDiff - height) * heightScalar * heightScalar * -4;

	return clamp(heightSignal, 0.0, 1.0);
}


// Utility function that maps a value from one range to another.
float remap( float originalValue, float originalMin, float originalMax, float newMin, float newMax){
	return newMin + (((originalValue - originalMin) / (originalMax - originalMin)) * (newMax - newMin));
}

float sampleCloudDensity(vec3 p, vec4 weatherData, bool highQuality, float LOD){

	float heightFraction = getHeightFractionForPoint(p, weatherData.gb);

	p += (m_WindDirection + m_WindTurbulanceFactor) * g_Time * m_WindSpeed;

	float cloudCoverage = weatherData.r; //Coverage
	float cloudHeight = weatherData.g; //0 - no height/not visible ---- 1 maximum height
	float cloudAltitude = weatherData.b; //0 - start at the begining of atmosphere ---- 1 - Clouds appear at maximum cloud layer

	//Scale the cloud coverage by modifier
	cloudCoverage *= m_CloudCoverage;

	float baseCloud = cloudCoverage;
	baseCloud *=heightFraction;

	if (baseCloud > .0 ){ //&& baseCloud < .75

		//Important! Must sample xzy!!!textureLod(m_CloudShapeNoise, p.xzy *.001 , 0);//
		vec4 lowFreqNoises = textureLod(m_CloudShapeNoise, p *.000077 , LOD).rgba;//*.001   .00008-looks-good  -- fat clouds 0000077   ---- best one -> 000077  -->00017  for erosion->0001337

		//Build FBM out of the high freq noises
		float fbm = (lowFreqNoises.g * .625) + (lowFreqNoises.b * 0.25) + (lowFreqNoises.a * .125);

		float baseCloudShape = remap(1.0-lowFreqNoises.r, -(1.0 - fbm), 1.0, 0.0, 1.0);
		baseCloud = remap(baseCloud, baseCloudShape, 1.0, 0.0, 1.0);


		//Curl & erosion
		if (highQuality){
			vec2 curlNoise = texture(m_Curl, p.xy*0.00007).xy;
			p.xy += curlNoise * (1.0 - heightFraction) * m_WindSpeed;

			vec3 highFreqNoises = textureLod(m_CloudErosionNoise, p *.00071 , LOD).rgb; //.0015  .007  --->00071

			float heightFractionWind = getHeightFractionForPoint(p, weatherData.gb);
			float highFreqFbm = (highFreqNoises.r * .625) + (highFreqNoises.g * 0.25) + (highFreqNoises.b * .125);

			float highFreqNoiseModifier = mix(highFreqFbm, 1-highFreqFbm, heightFractionWind);
			highFreqNoiseModifier *=.35;

			baseCloud = remap(baseCloud, highFreqNoiseModifier, 1.0, 0.0, 1.0);

			//Detail cloud erosion
			if (m_DetailedEdges && baseCloud < m_DetailedEdgeThreshold){
				float baseCloudDetail = clamp(baseCloud, 0, 1);
				highFreqFbm = mix(0, highFreqFbm, 1.0 - baseCloudDetail);
				baseCloud -=highFreqFbm;
			}
		}
	}
	return clamp(baseCloud, 0.0, 1.0);
}

void main() {

	vec3 dir = normalize(pos);

	//Bayer Filter step offset
	int a = int(gl_FragCoord.x) % 4;
	int b = int(gl_FragCoord.y) % 4;
	float stepOffset = bayerFilter[a * 4 + b];

	vec3 start = g_CameraPosition+dir*intersectSphere(g_CameraPosition, dir, m_CloudsFrom);
	vec3 end =   g_CameraPosition+dir*intersectSphere(g_CameraPosition, dir, m_CloudsTo );

	//The step size for the ray trace. Scaled by m_HorizonViewThreshold
	float stepDist = distance(start, end);
	float stepSize = (stepDist * m_HorizonViewThreshold) / m_RayMarchSteps;
	float lStepSize = stepSize;
	float tStep = stepSize * stepOffset;

	vec4 colorSum = vec4(0);
	float t = pow(1.0-0.7*dir.y, 15.0);

	vec4 weather = vec4(0);

	vec3 lightDir = m_LightDir;
	vec3 nLightDir = normalize(lightDir * lStepSize);

	float wScale = 0.00000919; //0000111 //try .000001 - mapped full sky .0001 - mapped 16x tiled ---->00005   --THIS-->000001 0.000025
	vec3 lightSum = vec3(0.0);
	//float lAlphaSum = 0.0;

	float costheta = dot(nLightDir, normalize(dir));
	float phase = mix(HG(costheta, 0.15),HG(costheta, -0.15), dir.y);//

	float cloudTest = 0.0;
	float zeroDensitySampleCount = 0;
	float density = 0.0;

	for(int i = 0; i < m_RayMarchSteps; i++) {

		//Sample the weather data
		vec3 p = start + dir * tStep;

		// Sample the weather texture
		vec2 pW = p.xz + m_WindDirection.xz * g_Time * m_WindSpeed * m_WeatherMapWindFactor;
		weather = texture(m_WeatherMap, pW * wScale);

		//If we are in cloud, sample density the expensive way
		if (cloudTest > 0.0){

			//calc density
			float sDensity = sampleCloudDensity(p, weather, true, 0.0);

			if (sDensity == 0.0){
				zeroDensitySampleCount++;
			}
			if (zeroDensitySampleCount != 6){
				density +=sDensity;
				tStep +=stepSize;
			}
			else{
				cloudTest = 0.0;
			    zeroDensitySampleCount = 0;
			}

			//Light integration
			vec3 lightPoint = vec3(pW.x, p.y, pW.y);
			//density along light ray
			float ldensity = 0.0;

			//calculate lighting samples to sun, only when sampled density is gt 0
			if (sDensity>0.0) {
				for (int j=0;j<6;j++) {
					lightPoint += (lightDir+(noiseKernel[j]*float(j+1))*lStepSize);

					//Take expensive sample
					if (ldensity< .17){
						ldensity += sampleCloudDensity(lightPoint, weather, true, 0);
					}
					//Cheap sample
					else{
						ldensity += sampleCloudDensity(lightPoint, weather, false, float(j));
					}
				}
				//Also take one long light step - inter cloud shadow - test this
				//lightPoint += lightDir * 9 + length(noiseKernel[int(gl_FragCoord.y) % 6]*lStepSize);
				//ldensity += sampleCloudDensity(lightPoint, weather, false, 3);
			}

			float alpha = smoothstep(.01, 1.0, sDensity) *.9; // simple alpha based on density
			vec3 cColor = vec3( 1.0, 1.0, 1.0 ); //base white cloud color - will probably need to change this up
		    alpha = (1.0-colorSum.w)*alpha;
		    colorSum += vec4(cColor*alpha, alpha);
		    //colorSum.rgb *=m_AmbientColor;

			float beers = exp(-ldensity);
			float pSug = 1.0 - exp(-ldensity * 2.0);
			float e = 2.0 * beers * pSug *phase;//try with and without phase
			lightSum +=  e *alpha;
		}
		//Sample the cloud density the cheap way
		else{
			cloudTest = sampleCloudDensity(p, weather, false, 6.0);
			if (cloudTest == 0.0){
				tStep +=stepSize;
			}
		}
	}

    vec4 lightEnergy = vec4(1.0 - lightSum, 1);
   lightEnergy *= pow(texture(m_SkyColor, fpPos), vec4(.33));

    //colorSum *= pow(texture(m_SkyColor, fpPos), vec4(1));
    //Blend option 1 - just blend the light energy without the background
    vec4 finalColor = mix( colorSum, lightEnergy , colorSum.w);

    //Blend option 2 - scale all the light energy by some constants - better for blending in other sky colors
    //vec4 finalColor = mix( colorSum, lightEnergy, .5);

    //Blend in sky color, ambient and sun colors
    finalColor.rgb *=pow(m_AmbientColor + m_SunColor, vec3(.33));	//TODO calculate ambient colors
    //finalColor.rgb *=pow(texture(m_SkyColor, fpPos).rgb, vec3(.35));


    gl_FragColor = finalColor;//;// finalColor;
}
