#define PI 3.141592
#define iSteps 16
#define jSteps 8

//
uniform vec3 g_CameraPosition;
uniform vec3 m_LightDir;

in vec3 pos;

vec3 skyColor( vec3 rd )
{
    vec3 sundir = normalize( m_LightDir );

    float yd = min(rd.y, 0.);
    rd.y = max(rd.y, 0.);

    vec3 col = vec3(0.);

    col += vec3(.4, .4 - exp( -rd.y*20. )*.3, .0) * exp(-rd.y*9.); // Red / Green
    col += vec3(.3, .5, .6) * (1. - exp(-rd.y*8.) ) * exp(-rd.y*.9) ; // Blue

    col = mix(col*1.2, vec3(.3),  1.-exp(yd*100.)); // Fog

    col += vec3(1.0, .8, .55) * pow( max(dot(rd,sundir),0.), 15. ) * .6; // Sun
    col += pow(max(dot(rd, sundir),0.), 150.0) *.15;

    return col;
}

void main() {

	//simple test sky
	vec3 color = skyColor( normalize(pos) );

    gl_FragColor =  vec4(color, 1);
}
