package com.dreamwagon.vclouds;

import com.jme3.app.SimpleApplication;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.InputListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.Trigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.scene.Node;

/**
 * 
 * @author J. Demarco
 *
 */
public class VCloudsApp extends SimpleApplication {

    private Vector3f lightDir = new Vector3f(-1,0.1f,1).normalizeLocal();

    VClouds cloudProcessor;
    
    float cloudCoverage = .5f;
    
    public static void main(String[] args) {
    	VCloudsApp app = new VCloudsApp();
        app.start();
    }

    @Override
    public void simpleInitApp() {

        //setDisplayFps(false);
        //setDisplayStatView(false);
        
        Node mainScene = new Node("Main Scene");
        rootNode.attachChild(mainScene);

        createTerrain(mainScene);
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(lightDir);
        sun.setColor(ColorRGBA.White.clone().multLocal(1f));
        mainScene.addLight(sun);
        
        AmbientLight al = new AmbientLight();
        al.setColor(new ColorRGBA(0.1f, 0.1f, 0.1f, 1.0f));
        mainScene.addLight(al);
        
        flyCam.setMoveSpeed(250);

        cam.setLocation(new Vector3f(-370.31592f, 182.04016f, 196.81192f));
        cam.setRotation(new Quaternion(0.015302252f, 0.9304095f, -0.039101653f, 0.3641086f));


        cloudProcessor= new VClouds(assetManager, cam);
        getViewPort().addProcessor(cloudProcessor);
         
        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
        fpp.addFilter(new FXAAFilter());
        
//      fpp.addFilter(new TranslucentBucketFilter());
        int numSamples = getContext().getSettings().getSamples();
        if (numSamples > 0) {
            fpp.setNumSamples(numSamples);
        }
        viewPort.addProcessor(fpp);

		key(KeyInput.KEY_1, new AnalogListener() {
			@Override
			public void onAnalog(String name, float value, float tpf) {
            	lightDir.y -=.01f;
			}
		});	
		key(KeyInput.KEY_2, new AnalogListener() {
			@Override
			public void onAnalog(String name, float value, float tpf) {
				lightDir.y +=.01f;
			}
		});	
		
		key(KeyInput.KEY_8, new AnalogListener() {
			@Override
			public void onAnalog(String name, float value, float tpf) {
            	if (cloudCoverage > .05) {
            		cloudCoverage -=.005f;
            	}
			}
		});	
		key(KeyInput.KEY_9, new AnalogListener() {
			@Override
			public void onAnalog(String name, float value, float tpf) {
            	if (cloudCoverage < .99) {
            		cloudCoverage +=.005f;
            	}
			}
		});	
    }

    private void createTerrain(Node rootNode) {
    	//Create some terrain
    }

    @Override
    public void simpleUpdate(float tpf) {
        super.simpleUpdate(tpf);
        
        cloudProcessor.setLightDir(lightDir);
        cloudProcessor.seCloudCoverage(cloudCoverage);
    }
    
	private static int actionc = 0;
    
    public void trigger(Trigger t, InputListener a) {
        trigger(t, a, "trigger_"+(++actionc));
    }
    public void trigger(Trigger t, InputListener a, String name) {
        inputManager.addMapping(name, t);
        inputManager.addListener(a, name);
    }
    public void key(int keyInput, InputListener a) {
        key(keyInput, a, "trigger_"+(++actionc));
    }
    public void key(int keyInput, InputListener a, String name) {
        trigger(new KeyTrigger(keyInput), a, name);
    }
}
