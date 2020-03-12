package com.dreamwagon.vclouds.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.jme3.math.ColorRGBA;
import com.jme3.texture.Image;
import com.jme3.texture.Image.Format;
import com.jme3.texture.image.ColorSpace;
import com.jme3.texture.image.ImageRaster;
import com.jme3.util.BufferUtils;
import com.sudoplay.joise.mapping.Array2Double;
import com.sudoplay.joise.mapping.Array2DoubleWriter;
import com.sudoplay.joise.mapping.IMappingUpdateListener;
import com.sudoplay.joise.mapping.Mapping;
import com.sudoplay.joise.mapping.MappingMode;
import com.sudoplay.joise.mapping.MappingRange;
import com.sudoplay.joise.module.ModuleAutoCorrect;
import com.sudoplay.joise.module.ModuleFractal;
import com.sudoplay.joise.module.ModuleGain;
import com.sudoplay.joise.module.ModulePow;
import com.sudoplay.joise.module.ModuleBasisFunction.BasisType;
import com.sudoplay.joise.module.ModuleBasisFunction.InterpolationType;
import com.sudoplay.joise.module.ModuleFractal.FractalType;
import com.sudoplay.joise.noise.Util.Vector3d;

/**
 * Generates a seamless weather map texture using noise 
 * 
 * @author J. Demarco
 *
 */
public class WeatherMapGenerator {
	
	static String FILE_PATH = "C:\\Users\\j_000\\Documents\\jmonkey-projects\\tests\\lowFreqNoise\\WORKING3\\";
	
	public static void main(String[] args) {
		
		generateWeatherTexure(1024, "Weather");
	}
	
	public static void generateWeatherTexure(int size, String fileName) {
		
		
		//create simplex noise for R G and G channel
    	ModuleAutoCorrect correctBaseClouds =generateWeather(BasisType.SIMPLEX, InterpolationType.QUINTIC, 3343, 1.73, 5, FractalType.HYBRIDMULTI);
    	ModuleAutoCorrect correctHighLevelClouds =generateWeather(BasisType.SIMPLEX, InterpolationType.QUINTIC, 25235, 3.54, 5, FractalType.HYBRIDMULTI);
    	
    	//ModuleAutoCorrect correct3 =generateWeather(BasisType.SIMPLEX, InterpolationType.QUINTIC, 63232, 5.5);
    	
    	correctBaseClouds.calculate4D();
    	correctHighLevelClouds.calculate4D();
    	
    	ModulePow modulePowBase = new ModulePow();
    	modulePowBase.setSource(correctBaseClouds);
    	modulePowBase.setPower(1.25);
        
        ModuleGain moduleGainBaseClouds = new ModuleGain();
        moduleGainBaseClouds.setSource(modulePowBase);
        moduleGainBaseClouds.setGain(.6);
        
    	ModulePow modulePowHigh = new ModulePow();
    	modulePowHigh.setSource(correctHighLevelClouds);
    	modulePowHigh.setPower(1.11);
        
        ModuleGain moduleGainHighClouds = new ModuleGain();
        moduleGainHighClouds.setSource(modulePowHigh);
        moduleGainHighClouds.setGain(.5);
        
        
    	Array2Double data1 =mapWeatherData(moduleGainBaseClouds, size);
    	Array2Double data2 =mapWeatherData(moduleGainHighClouds, size);
    	//Array2Double data3 = mapWeatherData(correct3, size);
    	
    	//Array2Double worlData =generateWorleySeamless2DFourDimension(1415, 3.77f, size);


    	com.jme3.texture.Image wImage = new com.jme3.texture.Image(Format.BGR8, size, size, 
    			BufferUtils.createByteBuffer(size * size * 4), null, ColorSpace.Linear);
    	ImageRaster wImageRaster = ImageRaster.create(wImage);
    	
		for (int x = 0; x < size; x++) {
			  for (int y = 0; y < size; y++) {
				//float R = (float)correct1.get(x / (float)size, y / (float)size);
				//float sample = (float)data1.get(x , y);
				//float worl = 1.0f - (float)worlData.get(x , y);
				
				//TODO - gen higher alt clouds. I do this in photoshop
				float highClouds =(float)data2.get(x , y);
				
			    float R = (float)data1.get(x , y);
			    float G = R;//(float)data2.get(x , y);
			    float B = 0;//highClouds;
			    
			    ColorRGBA cw = new ColorRGBA(R,G,B,1);
			    wImageRaster.setPixel(x, y, cw);
			  }
			}
			
		saveTexture(wImage, FILE_PATH + fileName);
	
	}
	
	public static ModuleAutoCorrect generateWeather(BasisType basisType, InterpolationType interpolationType, int seed, double frequency, int octaves, FractalType type) {

		ModuleFractal gen = new ModuleFractal();
		gen.setAllSourceBasisTypes(basisType);
		gen.setAllSourceInterpolationTypes(interpolationType);
		gen.setNumOctaves(octaves);
		gen.setFrequency(frequency);
		gen.setType(type);
		gen.setSeed(seed);
		
		/**
		* We auto-correct into the range [0, 1], performing 10,000 samples 
		* to calculate the auto-correct values.
		*/
		ModuleAutoCorrect source = new ModuleAutoCorrect(0, 1);
		source.setSource(gen);
		source.setSamples(10000);
		source.calculate2D();
		return source;
	}
	
	public static Array2Double mapWeatherData(com.sudoplay.joise.module.Module module, int size){
		
		MappingRange mappingRange = new MappingRange();
		mappingRange.map0 = new Vector3d(-2, -2, -1);
		mappingRange.map1 = new Vector3d(2, 2, 1);
		mappingRange.loop0 = new Vector3d(-1, -1, -1);
		mappingRange.loop1 = new Vector3d(1, 1, 1);
		
		Array2Double data = new Array2Double(size, size);
		Array2DoubleWriter writer = new Array2DoubleWriter(data);

		// map2D samples in 3D with a fixed z value
		Mapping.map2DNoZ (
		    MappingMode.SEAMLESS_XY, // the mapping mode
		    size, // the width of the final output
		    size, // the height of the final output
		    module, // the module to sample
		    mappingRange, // the range to sample from the noise
		    writer, // the IMapping2DWriter implementation
		    IMappingUpdateListener.NULL_LISTENER // the IMappingUpdateListener implementation
		   // 0.5 // fixed Z value
		);
		
		return data;
	}
	
    public static void saveTexture( Image jmeimage, String filePath)
    {
    	ImageRaster raster = ImageRaster.create(jmeimage);
    	int width = jmeimage.getWidth();
    	int height = jmeimage.getHeight();
    	
    	System.out.println("Saving image width: " + width + " height: " + height);
    	BufferedImage image = new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB
    			);
    	java.awt.Color tmpColor = null;
    	for (int x = 0; x < width ; x++) {
    		for (int y = 0; y < height ; y++) {
    			ColorRGBA color = raster.getPixel(x, y);
    			tmpColor = new java.awt.Color(color.r, color.g, color.b, color.a); 
    			image.setRGB(x, y, tmpColor.getRGB());
    		}
    	}
    	//Targa format
	    //File outputfile = new File(filePath);
    	/*int [] pixels = image.getRGB(0, 0, width, height, null, 0, width);
		byte [] buffer = TGAWriter.write(pixels, width, height, TGAReader.ARGB);
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(filePath + ".tga");
			fos.write(buffer);
			fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}*/

		
	    try {
	    	File outputfile = new File(filePath + ".png");
	        ImageIO.write(image, "png", outputfile);
	    } catch (IOException ex) {
	        ex.printStackTrace();
	    }
    }
}
