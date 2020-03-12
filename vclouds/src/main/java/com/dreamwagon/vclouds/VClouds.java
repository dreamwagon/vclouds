package com.dreamwagon.vclouds;

import java.util.Random;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix4f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderContext;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.shader.VarType;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture.Type;
import com.jme3.texture.Texture.WrapMode;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ImageRaster;

/**
 * @author J. Demarco
 *
 */
public class VClouds implements SceneProcessor{

	public static Random random = new Random();
	
	public float PLANET_RADIUS =6371e3f;
	public float SKY_RADIUS = 6471e3f;
	public float CLOUDS_FROM = 4000;
	public float CLOUDS_TO = 11000;
	
	public float RAYLEIGH_SCALE_HEIGHT = 7994f;
	public float MIE_SCALE_HEIGHT =1200f;
    
	public Vector3f sunColor = new Vector3f(1f, 1f, .874f);	
	public Vector3f ambientColor = new Vector3f(.45f, .9f, 1f);	
	public Geometry applyQuad;
	
	private Material skyMaterial;
    private Geometry skyGeometry;
    
    private Material cloudMaterial;
    private Geometry cloudGeometry;
    
	public RenderManager rm;
	public AssetManager am;
	public Camera cam;
	
	boolean init = false;
	
	ImageRaster skyImageRaster;
	private Texture2D skyViewTexture;
	private FrameBuffer skyViewFrameBuffer;
	private FrameBuffer boundRefFrameBuffer;
	
	public VClouds(AssetManager assetManager, Camera cam) {

		this.cam = cam;
		//Frame buffer scalar - decreasing the scale of the frame buffer increases performance at a loss of quality
		//setting to half size seems to be about right
		int frameBufferScalar = 2;
				
		int viewW = cam.getWidth()/frameBufferScalar;
		int viewH = cam.getHeight()/frameBufferScalar;	

		Image.Format fmt = Image.Format.RGBA32F;
		
		skyViewFrameBuffer = new FrameBuffer(viewW, viewH, 1);

		skyViewTexture = new Texture2D(viewW, viewH, fmt);
		skyViewTexture.setMinFilter(Texture.MinFilter.BilinearNearestMipMap);
		skyViewTexture.setMagFilter(Texture.MagFilter.Bilinear);
		skyViewTexture.getImage().setMipMapSizes(new int[]{0, 1});  	
		skyViewFrameBuffer.setColorTexture(skyViewTexture);
		
		skyImageRaster =  ImageRaster.create(skyViewTexture.getImage());
		
		applyQuad = createFullScreenQuad(0.99999f);
		Material applyMat = new Material(assetManager, "MatDefs/SkyRender/SkyApply.j3md");
		applyMat.setTexture("ColorMap", skyViewTexture);
		applyQuad.setMaterial(applyMat);
		
    	//Sky
		skyGeometry = createFullScreenQuad(cam.getWidth() / (float) cam.getHeight(), cam.getProjectionMatrix().invert());
        skyMaterial = new Material(assetManager, "MatDefs/Sky/Sky.j3md");
        skyGeometry.setMaterial(skyMaterial);
        skyGeometry.setCullHint(Spatial.CullHint.Never);
        
        skyMaterial.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        skyMaterial.getAdditionalRenderState().setDepthTest(false);
        skyMaterial.getAdditionalRenderState().setDepthWrite(false);
        skyMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);

        skyMaterial.setFloat("CloudsFrom", CLOUDS_FROM);
        skyMaterial.setFloat("CloudsTo", CLOUDS_TO);
        skyMaterial.setFloat("PlanetRadius", PLANET_RADIUS);
        skyMaterial.setFloat("SkyRadius", SKY_RADIUS);
        

        //Clouds
        cloudMaterial = new Material(assetManager, "MatDefs/Clouds/Clouds.j3md");
        cloudGeometry = createFullScreenQuad(cam.getWidth() / (float) cam.getHeight(), cam.getProjectionMatrix().invert());
        cloudGeometry.setCullHint(Spatial.CullHint.Never);
        cloudGeometry.setMaterial(cloudMaterial);
        
        cloudMaterial.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        cloudMaterial.getAdditionalRenderState().setDepthTest(false);
        cloudMaterial.getAdditionalRenderState().setDepthWrite(false);
        cloudMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.PremultAlpha);
        
        Texture weatherText = assetManager.loadTexture("Textures/Clouds/Weather.png");
        weatherText.setWrap(Texture.WrapMode.Repeat);
        weatherText.setMinFilter(Texture.MinFilter.BilinearNearestMipMap);
        weatherText.setMagFilter(Texture.MagFilter.Bilinear);
        cloudMaterial.setTexture("WeatherMap", weatherText);
        
        //Set cloud shape texture
        TextureKey noisekey = new TextureKey("Textures/Clouds/CloudShapeGenerated8.dds");
        noisekey.setGenerateMips(true);
        noisekey.setTextureTypeHint(Type.ThreeDimensional );

        Texture lowFreqNoiseTex3d = assetManager.loadTexture(noisekey);
        lowFreqNoiseTex3d.setMinFilter(Texture.MinFilter.BilinearNearestMipMap);
        lowFreqNoiseTex3d.setMagFilter(Texture.MagFilter.Bilinear);
        lowFreqNoiseTex3d.setWrap(WrapMode.Repeat);
        
        TextureKey highFreqNoisekey = new TextureKey("Textures/Clouds/CloudErosionGenerated8.dds");
        highFreqNoisekey.setGenerateMips(true);
        highFreqNoisekey.setTextureTypeHint(Type.ThreeDimensional );

        Texture highFreqNoiseTex3d = assetManager.loadTexture(highFreqNoisekey);
        highFreqNoiseTex3d.setMinFilter(Texture.MinFilter.BilinearNearestMipMap);
        highFreqNoiseTex3d.setMagFilter(Texture.MagFilter.Bilinear);
        highFreqNoiseTex3d.setWrap(WrapMode.Repeat);
        
        skyMaterial.setTexture("CloudShapeNoise", lowFreqNoiseTex3d);
        
        Texture curlNoise = assetManager.loadTexture("Textures/Clouds/Curlnoise.png");
        curlNoise.setWrap(WrapMode.Repeat);
        
        cloudMaterial.setTexture("CloudShapeNoise", lowFreqNoiseTex3d);
        cloudMaterial.setTexture("CloudErosionNoise", highFreqNoiseTex3d);
        
        cloudMaterial.setTexture("Curl", curlNoise);
       
        //Basic atmospheric settings. 
        cloudMaterial.setFloat("CloudsFrom", CLOUDS_FROM);
        cloudMaterial.setFloat("CloudsTo", CLOUDS_TO);
        cloudMaterial.setFloat("PlanetRadius", PLANET_RADIUS);
        cloudMaterial.setFloat("SkyRadius", SKY_RADIUS);
        
        //The number of steps to march through the atmosphere. This will greatly affect the detail of the
        //clouds, but also affects performance dramatically. Target is 128 but this will require optimization.
        //try 32, 64 or 96 
        cloudMaterial.setInt("RayMarchSteps", 128);
        
        //The coverage scalar. Modifies the coverage (R channel of weather texure) by this scalar
        cloudMaterial.setFloat("CloudCoverage", .5f);
        
        //Weather settings
        //Wind speed controls the sheering/shape changing effect on the clouds and is used as a 
        //factor with how fast cloud bodys move when animating the weather map
        cloudMaterial.setFloat("WindSpeed", 300.5f);
        
        //Controls how fast the weather map is animated in relation to the wind.
        //Set 0 to allow clouds to morph over stationary coverage. Higher values move the clouds across the sky
        cloudMaterial.setFloat("WeatherMapWindFactor", .75f);
        
        cloudMaterial.setVector3("WindDirection", new Vector3f(1.0f, 0.0f, 0.0f));
        //curl noise makes whisps that get added throughout. controls direction these whisps move
        cloudMaterial.setVector3("WindTurbulanceFactor", new Vector3f(0.0f, .1f, 0.0f));
        
        //Horizon view threshold controls how far to march in the direction of the horizon. 
        //This scales the size of the march steps. val of 1 will march only the minimum distance from camera. 
        //Higher values will march with larger steps to the horizon, revealing more clouds.
        //This value is dependent on the size of the atmosphere depth = (clouds_to - cloud_from)
        // Generally, you should use the lowest possible value you can, as higher values will degrade
        // near clouds and reveal far clouds. lower values will increase detail of near clouds
        cloudMaterial.setFloat("HorizonViewThreshold", 3f);
        
        //The number of cloud levels in the atmosphere. Up to 6 supported. If more required, modify the shader...
        cloudMaterial.setInt("AtmosphereLevels", 3);
        
        //The altitude levels for each cloud deck by % added to the lowest level. Deck 1=0% means this level will start at CLOUDS_FROM
        // 2=65% means level 2 will start at (CLOUDS_TO - CLOUDS_FROM) * .65
        cloudMaterial.setParam("AtmosphereLevelAltitudes", VarType.FloatArray, new float[]{.0f, .65f, .15f});
        
        //Heights are weighted by the number of levels. for example alt level 1 = 3 means this level height can span all three altitude layers.
        cloudMaterial.setParam("AtmosphereLevelHeights", VarType.FloatArray, new float[]{2f, 2f, 1f});

        //Use this to get finer detailed edges or stylized clouds
        cloudMaterial.setBoolean("DetailedEdges", true);
        //The threshold to use for the density cutoff point in the range of 0-1
        //This value is used to trim only the least dense part of the cloud. 0 is the least dense and 1 is the most.
        //Values of < .25 gives more whispy edges. values around .5 give more billowed looking clouds.
        cloudMaterial.setFloat("DetailedEdgeThreshold", .17f);
        
        cloudMaterial.setVector3("SunColor", sunColor);
        
        cloudMaterial.setVector3("AmbientColor", ambientColor);
        
        cloudMaterial.setTexture("SkyColor", skyViewTexture);
       
    }

    public void setLightDir(Vector3f lightDir) {
        skyMaterial.setVector3("LightDir", lightDir);
        cloudMaterial.setVector3("LightDir", lightDir);
    }
    
    public void seCloudCoverage(float cloudCoverage) {
    	cloudMaterial.setFloat("CloudCoverage", cloudCoverage);
    }
    
    
	public void renderView() {
			
		//Sky renderer is a hog!
		skyMaterial.render(skyGeometry, rm);
		if (skyViewTexture.getImage().getData(0) != null) {
		ColorRGBA skyAmbColor = skyImageRaster.getPixel(0,0);//(skyViewTexture.getImage().getHeight() /2, skyViewTexture.getImage().getWidth() /2);
		cloudMaterial.setVector3("AmbientColor", skyAmbColor.toVector3f());
		System.out.println("amb");
		}
		cloudMaterial.render(cloudGeometry, rm);
	}
	
	public void beginRenderView() {
		rm.setCamera(cam, cam.isParallelProjection());

		Renderer r = rm.getRenderer();

		boundRefFrameBuffer = getRenderContext(r).boundFB;

		r.setFrameBuffer(skyViewFrameBuffer);
//			r.setBackgroundColor(ColorRGBA.BlackNoAlpha);
		r.setBackgroundColor(ColorRGBA.Black);
		r.clearBuffers(true, false, false);
	}

	public void endRenderView() {
		Renderer r = rm.getRenderer();
		r.setFrameBuffer(boundRefFrameBuffer);
		boundRefFrameBuffer = null;
	}

	@Override
	public void initialize(RenderManager rm, ViewPort vp) {
		this.rm = rm;
		this.init = true;
	}

	@Override
	public void reshape(ViewPort vp, int w, int h) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isInitialized() {
		return init;
	}

	@Override
	public void preFrame(float tpf) {
		
	}

	@Override
	public void postQueue(RenderQueue rq) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void postFrame(FrameBuffer out) {
		//Swaps out the frame buffer
		beginRenderView();
		//render to our texture
		renderView();
		//Sets frame buffer back to renderer
		endRenderView();
		//render the quad with sky applied
		applyQuad.getMaterial().render(applyQuad, rm);
	}

	@Override
	public void cleanup() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setProfiler(AppProfiler profiler) {
		// TODO Auto-generated method stub
		
	}
	
    public static <T> T read(Object o, String name) {
        try {
            java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (T)f.get(o);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
	public static RenderContext getRenderContext(Renderer r) {
		return read(r, "context");
	}
	
	public static Geometry createFullScreenQuad(float z) {
		Mesh m = new Mesh();

		m.setBuffer(VertexBuffer.Type.Position, 3,
				new float[]{-1f, -1f, z,
					1f, -1f, z,
					-1f, 1f, z,
					1f, 1f, z
				});
		m.setBuffer(VertexBuffer.Type.TexCoord, 2,
				new float[]{0, 0,
					1f, 0,
					0, 1f,
					1f, 1f});
		m.setBuffer(VertexBuffer.Type.Index, 3,
				new short[]{0, 1, 2, 1, 3, 2});

		m.setStatic();
		m.updateBound();

		Geometry g = new Geometry("FSQuad", m);
		g.updateGeometricState();
		return g;
	}
	
	public static Geometry createFullScreenQuad(float aspect, Matrix4f projInv) {
		Mesh m = new Mesh();

		m.setBuffer(VertexBuffer.Type.Position, 3,
				new float[]{-1f, -1f, 0,
					1f, -1f, 0,
					-1f, 1f, 0,
					1f, 1f, 0
				});

		Vector4f a = projInv.mult(new Vector4f(1, 1, 1, 1));

		m.setBuffer(VertexBuffer.Type.TexCoord, 2,
				new float[]{-a.x, -a.y,
							 a.x, -a.y,
							-a.x,  a.y,
							 a.x,  a.y});
		m.setBuffer(VertexBuffer.Type.Index, 3,
				new short[]{0, 1, 2, 1, 3, 2});

		m.setStatic();
		m.updateBound();

		Geometry g = new Geometry("FSQuad", m);
		g.updateGeometricState();
		return g;
	}
}
