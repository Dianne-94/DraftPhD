package mainCodingFiles;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.BasicExtendedMetaData;
import org.eclipse.emf.ecore.util.ExtendedMetaData;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.m2m.atl.emftvm.EmftvmFactory;
import org.eclipse.m2m.atl.emftvm.ExecEnv;
import org.eclipse.m2m.atl.emftvm.Metamodel;
import org.eclipse.m2m.atl.emftvm.Model;
import org.eclipse.m2m.atl.emftvm.impl.resource.EMFTVMResourceFactoryImpl;
import org.eclipse.m2m.atl.emftvm.util.DefaultModuleResolver;
import org.eclipse.m2m.atl.emftvm.util.ModuleResolver;
import org.eclipse.m2m.atl.emftvm.util.TimingData;

/**
 * An off-the-shelf launcher for ATL/EMFTVM transformations
 * @author Victor Guana - guana@ualberta.ca
 * University of Alberta - SSRG Lab.
 * Edmonton, Alberta. Canada
 * Using code examples from: https://wiki.eclipse.org/ATL/EMFTVM
 */
public class ATLLauncher {
	
	// Some constants for quick initialization and testing.
	/*
	 * public final static String IN_METAMODEL = "./metamodels/Composed.ecore";
	 * public final static String IN_METAMODEL_NAME = "Composed"; public final
	 * static String OUT_METAMODEL = "./metamodels/Simple.ecore"; public final
	 * static String OUT_METAMODEL_NAME = "Simple";
	 * 
	 * public final static String IN_MODEL = "./models/composed.xmi"; public final
	 * static String OUT_MODEL = "./models/simple.xmi";
	 * 
	 * public final static String TRANSFORMATION_DIR = "./transformationRules/";
	 * public final static String TRANSFORMATION_MODULE= "Composed2Simple";
	 */
	static String IN_METAMODEL, IN_MODEL, OUT_METAMODEL, OUT_MODEL, TRANSFORMATION_DIR, TRANSFORMATION_MODULE;
	static String IN_METAMODEL_NAME  = "tcoel";  // must match ATL: IN : tcoel
	static String OUT_METAMODEL_NAME = "ctmc";   // must match ATL: OUT : ctmc

	
	// The input and output metamodel nsURIs are resolved using lazy registration of metamodels, see below.
	private String inputMetamodelNsURI;
	private String outputMetamodelNsURI;
	
	//Main transformation launch method
	public void launch(String inMetamodelPath, String inModelPath, String outMetamodelPath, String outModelPath, String transformationDir, String transformationModule){
		
		ExecEnv env = EmftvmFactory.eINSTANCE.createExecEnv();
		ResourceSet rs = new ResourceSetImpl();

		Metamodel inMetamodel = EmftvmFactory.eINSTANCE.createMetamodel();
		inMetamodel.setResource(rs.getResource(URI.createURI(inputMetamodelNsURI), true));
		env.registerMetaModel(IN_METAMODEL_NAME, inMetamodel);
		
		Metamodel outMetamodel = EmftvmFactory.eINSTANCE.createMetamodel();
		outMetamodel.setResource(rs.getResource(URI.createURI(outputMetamodelNsURI), true));
		env.registerMetaModel(OUT_METAMODEL_NAME, outMetamodel);
		
		/* Create and register resource factories to read/parse .xmi and .emftvm files, we need an .xmi parser because our in/output models are .xmi and our transformations are
		 * compiled using the ATL-EMFTV compiler that generates .emftvm files */
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("emftvm", new EMFTVMResourceFactoryImpl());

		// Load models
		Model inModel = EmftvmFactory.eINSTANCE.createModel();

		// inModelPath must be a NORMAL OS path, e.g. "D:/Case Study/28112025/1Easy_fireBrigade28112025.xmi"
		String fixedInPath  = inModelPath.replace("\\", "/");
		URI inURI           = URI.createFileURI(fixedInPath);
		inModel.setResource(rs.getResource(inURI, true));
		env.registerInputModel("IN", inModel);

		Model outModel = EmftvmFactory.eINSTANCE.createModel();
		String fixedOutPath = outModelPath.replace("\\", "/");
		URI outURI          = URI.createFileURI(fixedOutPath);
		outModel.setResource(rs.createResource(outURI));
		env.registerOutputModel("OUT", outModel);

		// --- Module resolver ---
		File dirFile = new File(transformationDir);
		System.out.println("Looking for EMFTVM module:");
		System.out.println("  dir   = " + dirFile.getAbsolutePath());
		System.out.println("  module= " + transformationModule);

		// Check .emftvm exist on disk
		File moduleFile = new File(dirFile, transformationModule + ".emftvm");
		System.out.println("  full  = " + moduleFile.getAbsolutePath());
		System.out.println("  exists= " + moduleFile.exists());

		// Important: EMFTVM suka URI "file:/..."
		String resolverPath = dirFile.toURI().toString();   // e.g. file:/C:/Users/V15/...
		System.out.println("  resolverPath = " + resolverPath);

		ModuleResolver mr = new DefaultModuleResolver(resolverPath, rs);

		TimingData td = new TimingData();
		env.loadModule(mr, transformationModule);
		td.finishLoading();
		env.run(td);
		td.finish();

			
		// Save models
		try {
			outModel.getResource().save(Collections.emptyMap());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private String lazyMetamodelRegistration(String metamodelPath){
		
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
   	
	    ResourceSet rs = new ResourceSetImpl();
	    // Enables extended meta-data, weird we have to do this but well...
	    final ExtendedMetaData extendedMetaData = new BasicExtendedMetaData(EPackage.Registry.INSTANCE);
	    rs.getLoadOptions().put(XMLResource.OPTION_EXTENDED_META_DATA, extendedMetaData);
	
	    Resource r = rs.getResource(URI.createFileURI(metamodelPath), true);
	    EObject eObject = r.getContents().get(0);
	    // A meta-model might have multiple packages we assume the main package is the first one listed
	    if (eObject instanceof EPackage) {
	        EPackage p = (EPackage)eObject;
	        System.out.println(p.getNsURI());
	        EPackage.Registry.INSTANCE.put(p.getNsURI(), p);
	        return p.getNsURI();
	    }
	    return null;
	}
	
	/* As shown above we need the inputMetamodelNsURI and the outputMetamodelNsURI to create the context of the transformation, so we simply use the return value of lazyMetamodelRegistration to store them.
	 * -- Notice that the lazyMetamodelRegistration(..) implementation may return null in case it doesn't find a package in the given metamodel, so watch out for malformed metamodels. */
	public void registerInputMetamodel(String inputMetamodelPath){	
		inputMetamodelNsURI = lazyMetamodelRegistration(inputMetamodelPath);
	}

	public void registerOutputMetamodel(String outputMetamodelPath){
		outputMetamodelNsURI = lazyMetamodelRegistration(outputMetamodelPath);
	}
	
	/* A test main method, I'm using constants so I can quickly change the case study by simply modifying the header of the class. */	
	public static void main(String ... args){
		ATLLauncher l = new ATLLauncher();
		l.registerInputMetamodel(IN_METAMODEL);
		l.registerOutputMetamodel(OUT_METAMODEL);
		l.launch(IN_METAMODEL, IN_MODEL, OUT_METAMODEL, OUT_MODEL, TRANSFORMATION_DIR, TRANSFORMATION_MODULE);
	}
}
