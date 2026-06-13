package mainCodingFiles;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.FlowLayout;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.xml.type.impl.XMLTypeFactoryImpl;
import org.eclipse.m2m.atl.emftvm.impl.resource.EMFTVMResourceFactoryImpl;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;

public class MainFrame extends JFrame {
	private JPanel contentPane;
	private String selectedFilePath, moduleName;
	private File[] selectedFiles;
	private int iterationCounter = 1; // Counter for iteration number for file
	private int globalMaxX = -1;
	private int globalMaxY = -1;
	private JTextArea inputTextArea, outputTextArea, outTextSM, outTextProp;
	private JScrollPane scrollInput, scrollOutput, scrollOutputSM, scrollOutputProp;
	private JButton uploadButton, btnGenerate, convertButton, xmiPrismButton, analysisButton, btnSave, btnReset;
	private Process process;
	private PrismExportBar exportBar; // needs its own .java file in same package
	private String runId = null; // unique folder per run (e.g., out/2025-09-01_12-01-33)
	private String ctmcXmiPath = null; // prefer CTMC XMI for state/transition counts
	private int gridX, gridY;
	private String fixed, validatedContent;
	Pattern headerPattern, ofDim, arr, px, py, gridPattern, ensemblePattern, componentPattern, rolePattern, statePattern, transitionPattern, conditionPattern, traitPattern, utilityPattern, positionPattern;
	long convertTime, memoryConvertUsed, convertPRISMTime, memoryConvertPRISMused, convertPropsTime, memoryConvertPropsused, OverallConvertPrismTime, OverallConvertPrismMemUsed, verifyTime, verifyMemUsed;

	private static final String CORRECT_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<tcoel:TCOEL xmi:version=\"2.0\"\n" + "    xmlns:xmi=\"http://www.omg.org/XMI\"\n"
			+ "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
			+ "    xmlns:tcoel=\"http://example/tcoel\"\n"
			+ "    xsi:schemaLocation=\"/convertTcoel2Ctmc/tcoel.ecore tcoel.ecore\">";

	protected long startConvert, memStartConvert, endConvert, memEndConvert, elaspedConvert, memoryConvert,
			startXtractNgenerate, memXtractNgenerate, endXtractNgenerate, elapsedXtractNgenerate, memoryXtractNgenerate,
			memStartXtractNgenerate, memEndXtractNgenerate, startGeneratePrism, memStartGeneratePrism, endGeneratePrism,
			memEndGeneratePrism;
	protected long startVerifyPrism, memStartVerifyPrism, endVerifyPrism, memEndVerifyPrism, startConvertPrismModel, verifyEndNano, verifyStartNano,
			memConvertStartPrismModel, endConvertPrismModel, memConvertEndPrismModel, endConvertProps, verifyEnd, verifyStart,
			memEndConvertProps, memStartConvertProps, startConvertProps;
	public File file;
	public Scanner scanner;
	public JLabel lblUserInputFile, inputTextlbl, OutputTextXMI, OutputTextSM, lblOutputFileText;
	public static JTextField pathText;

	Random random = new Random();
	Set<String> inGridSize, rootEnsembleNames, ensembleNames, coorEnsembleNames, componentNames, componentPosition, roleNames,
			sharedStateNames, transitionRate, transitionNames, stateNames, conditions, memberships, performAction,
			traitInformation, normalizedTraits, cardinalities;
	Map<String, String[]> cardinalityRanges;
	Map<String, int[]> roleCardinalities;
	Map<String, String> utilityValue, fallbackPositionMap;
	String gridSize, stateNum, stateName, fromState, toState, inMetamodelPath, outMetamodelPath, uniqueID, outModelPath,
			transformationDir, transformationModule, className, classBody, savePath, xmiContent, prismModelPath,
			resultFolder, prismCommand, propertyFile;
	List<String> coorPrefixes, coorBaseList;

	public MainFrame() {
		setBackground(SystemColor.activeCaption);

		setTitle("MT Input and Output Display");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// Set the bounds of the frame - (x, y, width, height)
		setBounds(15, 90, 1905, 653); // width: 800, height: 600

		// Initialize the content pane and set its border
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);

		inputTextArea = new JTextArea();
		inputTextArea.setTabSize(3);
		inputTextArea.setLineWrap(true);
		inputTextArea.setFont(new Font("Times New Roman", Font.PLAIN, 12));
		inputTextArea.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		scrollInput = new JScrollPane(inputTextArea);
		scrollInput.setBounds(24, 82, 375, 518);
		contentPane.add(scrollInput);

		outputTextArea = new JTextArea();
		outputTextArea.setTabSize(3);
		outputTextArea.setLineWrap(true);
		outputTextArea.setFont(new Font("Times New Roman", Font.PLAIN, 12));
		outputTextArea.setEditable(false); // Make text area read-only
		outputTextArea.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		scrollOutput = new JScrollPane(outputTextArea);
		scrollOutput.setBounds(701, 83, 374, 517);
		contentPane.add(scrollOutput);

		outTextSM = new JTextArea();
		outTextSM.setLineWrap(true);
		outTextSM.setFont(new Font("Times New Roman", Font.PLAIN, 12));
		outTextSM.setEditable(false); // Make text area read-only
		outTextSM.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		scrollOutputSM = new JScrollPane(outTextSM);
		scrollOutputSM.setBounds(1101, 82, 374, 517);
		contentPane.add(scrollOutputSM);

		outTextProp = new JTextArea();
		outTextProp.setLineWrap(true);
		outTextProp.setFont(new Font("Times New Roman", Font.PLAIN, 12));
		outTextProp.setEditable(false);
		outTextProp.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		scrollOutputProp = new JScrollPane(outTextProp);
		scrollOutputProp.setBounds(1501, 83, 372, 515);
		contentPane.add(scrollOutputProp);

		lblUserInputFile = new JLabel("User Input File:");
		lblUserInputFile.setFont(new Font("Tahoma", Font.BOLD, 15));
		lblUserInputFile.setBounds(73, 23, 144, 20);
		contentPane.add(lblUserInputFile);

		inputTextlbl = new JLabel("Input File Text");
		inputTextlbl.setFont(new Font("Tahoma", Font.BOLD, 15));
		inputTextlbl.setBounds(152, 58, 115, 20);
		// frame.getContentPane().add(inputTextlbl);
		contentPane.add(inputTextlbl);

		OutputTextXMI = new JLabel("Output File Text (.xmi)");
		OutputTextXMI.setFont(new Font("Tahoma", Font.BOLD, 15));
		OutputTextXMI.setBounds(800, 58, 170, 20);
		// frame.getContentPane().add(OutputTextlbl);
		contentPane.add(OutputTextXMI);

		OutputTextSM = new JLabel("Output File Text (.sm)");
		OutputTextSM.setFont(new Font("Tahoma", Font.BOLD, 15));
		OutputTextSM.setBounds(1200, 58, 170, 20);
		contentPane.add(OutputTextSM);

		lblOutputFileText = new JLabel("Output File Text (.props)");
		lblOutputFileText.setFont(new Font("Tahoma", Font.BOLD, 15));
		lblOutputFileText.setBounds(1595, 58, 185, 20);
		contentPane.add(lblOutputFileText);

		// display file path that user input
		pathText = new JTextField();
		pathText.setBounds(218, 25, 638, 20);
		contentPane.add(pathText);
		pathText.setColumns(10);

		exportBar = new PrismExportBar();
		exportBar.cbScaling.setSelected(false);
		exportBar.cbProps.setSelected(false);
		exportBar.cbMetrics.setSelected(false);
		exportBar.cbTrace.setSelected(false);
		exportBar.cbParse.setSelected(false);
		exportBar.cbEnv.setSelected(false);
		FlowLayout flowLayout = (FlowLayout) exportBar.getLayout();
		exportBar.setBounds(443, 354, 218, 211); // tweak position/width as you like
		contentPane.add(exportBar);

		// Upload File Button
		uploadButton = new JButton("Upload File");
		uploadButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				browseFile();
			}
		});
		uploadButton.setBounds(905, 23, 160, 23);
		contentPane.add(uploadButton);

		// Generate File Button
		btnGenerate = new JButton("Generate XMI (Input)");
		btnGenerate.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (selectedFiles != null) {
					for (File file : selectedFiles) {
						pathText.setText(file.getAbsolutePath());

						startXtractNgenerate = System.currentTimeMillis();
						memStartXtractNgenerate = Runtime.getRuntime().totalMemory()
								- Runtime.getRuntime().freeMemory();

						xtractNgenerate();

						endXtractNgenerate = System.currentTimeMillis();
						memEndXtractNgenerate = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
						elapsedXtractNgenerate = endXtractNgenerate - startXtractNgenerate;
						memoryXtractNgenerate = (memEndXtractNgenerate - memStartXtractNgenerate) / 1024;

						System.out.println("Time to generate XMI: " + elapsedXtractNgenerate + " ms, Memory used: "
								+ memoryXtractNgenerate + " KB");
					}
				} else {
					JOptionPane.showMessageDialog(null, "Please select file(s) first.", "Warning",
							JOptionPane.WARNING_MESSAGE);
				}
			}
		});
		btnGenerate.setBounds(1099, 23, 170, 23);
		btnGenerate.setVisible(false);
		contentPane.add(btnGenerate);

		// Convert Button
		convertButton = new JButton("Convert Input (XMI) to Output (XMI)");
		convertButton.setFont(new Font("Tahoma", Font.PLAIN, 10));
		convertButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				convert();
			}
		});

		convertButton.setBounds(443, 120, 218, 23);
		convertButton.setVisible(true);
		contentPane.add(convertButton);

		// Generate XMIPrism File Button
		xmiPrismButton = new JButton("Convert Output (XMI) to Output (SM)");
		xmiPrismButton.setFont(new Font("Tahoma", Font.PLAIN, 10));
		xmiPrismButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				startGeneratePrism = System.currentTimeMillis();
				memStartGeneratePrism = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

				handleGeneratePrism();

				endGeneratePrism = System.currentTimeMillis();
				memEndGeneratePrism = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
				System.out.println("XMI to PRISM conversion time: " + (endGeneratePrism - startGeneratePrism)
						+ " ms, Memory used: " + ((memEndGeneratePrism - memStartGeneratePrism) / 1024) + " KB");
			}
		});
		xmiPrismButton.setBounds(443, 165, 218, 23);
		contentPane.add(xmiPrismButton);

		// open model checking Button
		analysisButton = new JButton("Verify Output File");
		analysisButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				startVerifyPrism = System.currentTimeMillis();
				memStartVerifyPrism = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
				verifyPrism();
				endVerifyPrism = System.currentTimeMillis();
				memEndVerifyPrism = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
				System.out.println("PRISM Verification Time: " + (endVerifyPrism - startVerifyPrism)
						+ " ms, Memory used: " + ((memEndVerifyPrism - memStartVerifyPrism) / 1024) + " KB");
				printSummaryStats();
			}
		});
		analysisButton.setBounds(443, 210, 218, 23);
		contentPane.add(analysisButton);

		// Save File Button
		btnSave = new JButton("Save Output Result");
		btnSave.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveOutput();
			}
		});
		btnSave.setBounds(443, 255, 218, 23);
		// frame.getContentPane().add(btnSave);
		contentPane.add(btnSave);

		// Reset Button
		btnReset = new JButton("Reset");
		btnReset.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearAll();
			}
		});
		btnReset.setBounds(443, 305, 218, 23);
		contentPane.add(btnReset);

	}

	// Convert Method
	protected void convert() {
		// TODO Auto-generated method stub
		if (pathText.getText() != null && !pathText.getText().trim().isEmpty()) {
			try {
				// Register resource factories for .ecore, .xmi, .emftvm
				Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
				Map<String, Object> m = reg.getExtensionToFactoryMap();
				m.put("ecore", new EcoreResourceFactoryImpl());
				m.put("xmi", new XMIResourceFactoryImpl()); 
				m.put("emftvm", new EMFTVMResourceFactoryImpl());

				ATLLauncher launcher = new ATLLauncher();

				// Prepare the paths for metamodels, models, and transformation details
				inMetamodelPath = "C:/Users/V15/eclipse-workspace/PHDMadinahNewUpdates/metamodels/tcoel.ecore";
				outMetamodelPath = "C:/Users/V15/eclipse-workspace/PHDMadinahNewUpdates/metamodels/ctmc.ecore";
				uniqueID = String.valueOf(iterationCounter++); // Increment counter for unique ID
				outModelPath = "C:/Users/V15/eclipse-workspace/PHDMadinahNewUpdates/models/ctmc_" + uniqueID + ".xmi";
				transformationDir = "C:/Users/V15/eclipse-workspace/PHDMadinahNewUpdates/transformationRules/";
				// transformationModule = "rules"; //original and runnable without issues
				// transformationModule = "rules200625"; //updated new rules on 20.06.2025
				// transformationModule = "rules27062025"; // updated code270625
				// transformationModule = "rules27062026_refined";// updated code 20082025
				//transformationModule = "transformationRules01122025";// updated code 01122025
				transformationModule = "transformationRulesTraitAware13022026";// updated code 13022026

				// Register input/output metamodels
				launcher.registerInputMetamodel(inMetamodelPath);
				launcher.registerOutputMetamodel(outMetamodelPath);

				System.out.println("the pathtext:" + pathText.getText());

				// time and memory tracking for testing usage
				startConvert = System.currentTimeMillis();
				memStartConvert = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

				// Launch the transformation based on file type
				launcher.launch(inMetamodelPath, pathText.getText(), outMetamodelPath, outModelPath, transformationDir,	transformationModule);

				endConvert = System.currentTimeMillis();
				memEndConvert = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
				System.out.println("ATL Conversion Time: " + (endConvert - startConvert) + " ms, Memory used: "	+ ((memEndConvert - memStartConvert) / 1024) + " KB");

				// Display the output XMI content in the outputTextArea
				displayFileContent(new File(outModelPath), outputTextArea);

				// JOptionPane.showMessageDialog(null, "Conversion successful!");
			} catch (Exception ex) {
				ex.printStackTrace();
				JOptionPane.showMessageDialog(null, "Conversion failed: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		} else {
			JOptionPane.showMessageDialog(null, "Please select a file first.", "Warning", JOptionPane.WARNING_MESSAGE);
		}
	}

	private void handleGeneratePrism() {
		try {
			String xmiContent = outputTextArea.getText().trim();
			if (xmiContent.isEmpty()) {
				JOptionPane.showMessageDialog(this, "No XMI content to convert!", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			PrismGenerator prismGen = new PrismGenerator();
			// PrismGenerator2 prismGen = new PrismGenerator2();
			prismGen.generatePrism(xmiContent, outputTextArea, outTextSM, outTextProp);

		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error during PRISM generation: " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// Browse and load one or more files
	protected void browseFile() {
	    JFileChooser fileChooser = new JFileChooser(new File("D:\\Case Study\\"));
	    fileChooser.setMultiSelectionEnabled(true);
	    int returnValue = fileChooser.showOpenDialog(null);

	    if (returnValue == JFileChooser.APPROVE_OPTION) {
	        selectedFiles = fileChooser.getSelectedFiles();

	        inputTextArea.setText(""); // Clear previous content
	        StringBuilder allPath = new StringBuilder();

	        // Always check first file type (works for single or multiple)
	        if (selectedFiles.length > 0) {
	            checkingFileType(selectedFiles[0]);
	        }

	        for (File file : selectedFiles) {
	            String ext = getFileExtension(file.getName());

	            // For .scala → display directly from file
	            if (ext.equalsIgnoreCase("scala")) {
	                inputTextArea.append("===== " + file.getName() + " =====\n");
	                displayFileContent(file, inputTextArea);
	                inputTextArea.append("\n\n");
	            }
	            
	            // For .zip → display directly from file
	            else if (ext.equalsIgnoreCase("zip")) {
	                inputTextArea.append("===== " + file.getName() + " =====\n");
	                //displayFileContent(file, inputTextArea);
	                readZipFile(file, inputTextArea);
	                inputTextArea.append("\n\n");
	            }

	            // ✅ For .xmi or .txt → display only validated version
	            else if (ext.equalsIgnoreCase("xmi") || ext.equalsIgnoreCase("txt")) {
	               // inputTextArea.append("===== " + file.getName() + " (validated) =====\n");
	                if (validatedContent != null && !validatedContent.isBlank()) {
	                    inputTextArea.append(validatedContent + "\n\n");
	                }
	            }
	         // Build combined path text
	            allPath.append(file.getAbsolutePath()).append("; ");
	        }

	        // Clean and set pathText safely
	        pathText.setText(cleanPathText(allPath.toString()));
	    }
	}
	
	// Display content of a single file
	private void displayFileContent(File file, JTextArea textArea) {
		try (Scanner scanner = new Scanner(file)) {
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				textArea.append(line + "\n");
			}
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(null, "File not found: " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// For multiple files
	private void displayFileContent(File[] files, JTextArea textArea) {
		for (File f : files) {
			displayFileContent(f, textArea);
		}
	}
	
	private void readZipFile(File zipFile, JTextArea textArea) {

	    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
	        ZipEntry entry;
	        while ((entry = zis.getNextEntry()) != null) {
	            String entryName = entry.getName();
	            // Skip folders
	            if (entry.isDirectory()) {
	                continue;
	            }
	            //textArea.append("\n====== FILE: " + entryName + "======\n");

	            // Only read supported text/code files
	            if (entryName.endsWith(".scala") ||entryName.endsWith(".java") || entryName.endsWith(".xml") ||entryName.endsWith(".xmi") ||entryName.endsWith(".txt")) {
		            textArea.append("\n====== " + entryName + " ======\n");
	                BufferedReader br = new BufferedReader(new InputStreamReader(zis));
	                String line;
	                while ((line = br.readLine()) != null) {
	                    textArea.append(line + "\n");
	                }
	                textArea.append("\n");
	            } 
	            else {
	                //textArea.append("[Skipped unsupported/binary file]\n");
	            }
	            zis.closeEntry();
	        }

	    } catch (Exception e) {

	        textArea.append("\nERROR READING ZIP:\n");
	        textArea.append(e.getMessage() + "\n");
	    }
	}
	
	private void checkingFileType(File file) {
	    String fileExtension = getFileExtension(file.getName());
	    inputTextArea.setText(""); // Always clear previous content

	    if (fileExtension.equalsIgnoreCase("scala")|| fileExtension.equalsIgnoreCase("zip")) {
	        // Show Generate button for Scala files
	        btnGenerate.setVisible(true);
	        this.validatedContent = null; // No validation for Scala
	    } 
	    else if (fileExtension.equalsIgnoreCase("xmi") || fileExtension.equalsIgnoreCase("txt")) {
	        // Hide Generate button for model files
	        btnGenerate.setVisible(false);

	        // Validate and fix XMI header — use validated content only
			String validated = XMIAutoPipeline.handleUserInput(file);
			this.validatedContent = validated;
	    } 
	    else {
	        btnGenerate.setVisible(false);
	        JOptionPane.showMessageDialog(
	            null,
	            "Unsupported file type: " + fileExtension,
	            "Warning",
	            JOptionPane.WARNING_MESSAGE
	        );
	    }
	}
	
	private String cleanPathText(String pathList) {
	    if (pathList == null || pathList.isBlank()) return "";

	    // Trim spaces
	    String clean = pathList.trim();

	    // Remove trailing semicolon and spaces
	    if (clean.endsWith(";")) {
	        clean = clean.substring(0, clean.length() - 1).trim();
	    }

	    // Only keep the first file path (ATL/EMF needs one clean path)
	    int idx = clean.indexOf(';');
	    if (idx != -1) {
	        clean = clean.substring(0, idx).trim();
	    }

	    return clean;
	}

	public String checkAndFixHeader(File inputFile) throws IOException {
		// Step 1: Read file content
		String content = Files.readString(inputFile.toPath(), StandardCharsets.UTF_8);

		// Step 2: Extract user’s header block (everything before first <ensemble>, <component>, or <environment>)
		headerPattern = Pattern.compile("(?s)^.*?(?=<\\s*(ensemble|component|environment)\\b)",	Pattern.CASE_INSENSITIVE);
		Matcher matcher = headerPattern.matcher(content);

		String body = content;
		String userHeader = "";

		if (matcher.find()) {
			userHeader = matcher.group(0).trim();
			body = content.substring(matcher.end()).trim();
			
			// 🔹 Step 3: Normalize both headers for comparison (ignore spacing)
			if (!normalize(userHeader).equals(normalize(CORRECT_HEADER))) {
				// Replace entire block with correct header
				fixed = CORRECT_HEADER + "\n" + body;
				// Make sure we have closing tag
				if (!fixed.contains("</tcoel:TCOEL>")) {
					fixed += "\n</tcoel:TCOEL>";
				}
				// Overwrite same file
				Files.writeString(inputFile.toPath(), fixed, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);;
			}
		}
		return fixed;
	}

	private String normalize(String text) {
		return text.replaceAll("\\s+", " ").trim();
	}

	private String getFileExtension(String fileName) {
		int lastDotIndex = fileName.lastIndexOf('.');
		return (lastDotIndex == -1) ? "" : fileName.substring(lastDotIndex + 1).toLowerCase();
	}

	protected void saveOutput() {
		String saveOutputPath = "D:\\BACKUP\\eclipse-workspace\\ATLauncherAmrina\\ATLauncher\\models\\";
		String outputXMI = "outputXMI";
		String outputSM = "outputSM";

		// Ensure the directory exists
		File directory = new File(saveOutputPath);
		if (!directory.exists()) {
			directory.mkdirs(); // Create the directory if it does not exist
		}

		String XMIOuput = getUniqueFileName(saveOutputPath, outputXMI, ".xmi");
		String SMOuput = getUniqueFileName(saveOutputPath, outputSM, ".sm");

		saveTextToFile(outputTextArea.getText(), XMIOuput);
		saveTextToFile(outTextSM.getText(), SMOuput);
	}

	private String getUniqueFileName(String saveOutputPath, String baseName, String extension) {
		String fileName = baseName + extension;
		int counter = 1;

		while (new File(fileName).exists()) {
			// Increment and create a new filename
			fileName = saveOutputPath + baseName + "_" + (++counter) + extension;
		}
		return fileName;
	}

	private void saveTextToFile(String text, String fileName) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
			writer.write(text);
			JOptionPane.showMessageDialog(this, "Text saved to " + fileName);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "Error saving to file: " + e.getMessage());
		}
	}

	/* Remove the Comment in the user input file */
	private String removeComments(String input) {

		input = input.replaceAll("(?s)/\\*.*?\\*/", ""); // Remove block comments (/* ... */)
		input = input.replaceAll("(?m)//.*?$", "");// Remove single-line comments (//...)

		return input;
	}
	
	/* ============================= Extract + Generate XMI (Hybrid: Multithreaded + Original Logic) ============================= */
	protected void xtractNgenerate() {
	    try {
	        long startXtractNgenerate = System.currentTimeMillis();
	        memStartXtractNgenerate = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

	        if (selectedFiles == null || selectedFiles.length == 0) {
	            JOptionPane.showMessageDialog(MainFrame.this, "No file selected. Please choose one or more files to proceed!", "Error", JOptionPane.ERROR_MESSAGE);
	            return;
	        }

	        // 1. Initialize all collections
	        rootEnsembleNames = new LinkedHashSet<>();
	        ensembleNames = new LinkedHashSet<>();
	        coorEnsembleNames = new LinkedHashSet<>();
	        componentNames = new LinkedHashSet<>();
	        componentPosition = new LinkedHashSet<>();
	        roleNames = new LinkedHashSet<>();
	        sharedStateNames = new LinkedHashSet<>();
	        transitionRate = new LinkedHashSet<>();
	        transitionNames = new LinkedHashSet<>();
	        stateNames = new LinkedHashSet<>();
	        conditions = new LinkedHashSet<>();
	        memberships = new LinkedHashSet<>();
	        performAction = new LinkedHashSet<>();
	        traitInformation = new LinkedHashSet<>();
	        normalizedTraits = new LinkedHashSet<>();
	        cardinalities = new LinkedHashSet<>();
	        cardinalityRanges = new HashMap<>();
	        roleCardinalities = new HashMap<>();
	        utilityValue = new HashMap<>();
	        fallbackPositionMap = new HashMap<>();
	        inGridSize = new LinkedHashSet<>();

	        // ============================= 2. Multithreaded extraction setup =============================
	        int numThreads = Math.min(selectedFiles.length, Runtime.getRuntime().availableProcessors());
	        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
	        List<Future<Map<String, Object>>> futures = new ArrayList<>();

	        for (File file : selectedFiles) {
	            if (!file.getName().endsWith(".scala")) continue;

	            futures.add(executor.submit(() -> {
	                // Each thread’s local data
	                Map<String, Object> result = new HashMap<>();
	                Set<String> localRootEnsembles = new LinkedHashSet<>();
	                Set<String> localCoorEnsembleNames = new LinkedHashSet<>();
	                Map<String, String> ensembleToCoordinatorInstance = new HashMap<>();
	                Map<String, Integer> coordinatorCounter = new HashMap<>();
	                Set<String> localEnsembles = new LinkedHashSet<>();
	                Set<String> localComponents = new LinkedHashSet<>();
	                Set<String> localPositions = new LinkedHashSet<>();
	                Set<String> localRoles = new LinkedHashSet<>();
	                Set<String> localStates = new LinkedHashSet<>();
	                Set<String> localTransitions = new LinkedHashSet<>();
	                Set<String> localRates = new LinkedHashSet<>();
	                Set<String> localConditions = new LinkedHashSet<>();
	                Set<String> localMemberships = new LinkedHashSet<>();
	                Set<String> localTraits = new LinkedHashSet<>();
	                Map<String, String> localUtility = new HashMap<>();
	                Map<String, String[]> localCardinalityRanges = new HashMap<>();
	                Map<String, int[]> localRoleCard = new HashMap<>();
	                int gridXLocal = 0, gridYLocal = 0;

	                // === Load and clean ===
	                String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	                String content = removeComments(raw);
	                BufferedReader reader = new BufferedReader(new StringReader(content));

	                // === Setup patterns (from your working version) ===
	                Pattern gridPattern = Pattern.compile( "(?i)MapSize\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)" + "|mapSize\\s*=\\s*\\((\\d+)\\s*,\\s*(\\d+)\\)" + "|grid\\s*(\\d+)\\s*[xX]\\s*(\\d+)");
	                Pattern positionPattern = Pattern.compile("(?i)Position\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");
	                Pattern pattern = Pattern.compile("(?i)(?:class|object)\\s+(\\w+)\\s*(\\([^)]*\\))?\\s*(?:extends\\s+|=\\s*new\\s+)?(Component|Ensemble|RootEnsemble)\\s*\\{?");
	                Pattern coordinatorPattern = Pattern.compile("(?i)class\\s+(\\w+)\\s*\\(([^)]*coordinator\\s*:\\s*(\\w+)[^)]*)\\)");
	                Pattern rolePattern = Pattern.compile("(?i)role\\(\\s*\"(\\w+)\"\\s*(?:,.*)?\\)");
	                Pattern statePattern = Pattern.compile("(?i)(\\w+)\\s*=\\s*(State|StateOr\\(.*?\\))");
	                Pattern transitionPattern = Pattern.compile("(?i)Transition\\s*\\(\\s*\"(\\w+)\".*?([0-9.]+)");
	                Pattern conditionPattern = Pattern.compile("(?i)Condition\\(\"(.*?)\",\\s*\"(.*?)\"\\)");
	                Pattern traitPattern = Pattern.compile("(?i)trait\\s+(\\w+)|traits\\s*=\\s*Seq\\(([^)]*)\\)");
	                Pattern utilityPattern = Pattern.compile("(?i)utility\\s*=\\s*([0-9.]+)");

	                // === Read each line ===
	                String line;
	                while ((line = reader.readLine()) != null) {
	                    line = line.trim();
	                    if (line.isEmpty()) continue;

	                    Matcher mGrid = gridPattern.matcher(line);
	                    while (mGrid.find()) {
	                        for (int i = 1; i <= mGrid.groupCount(); i += 2) {
	                            if (mGrid.group(i) != null && mGrid.group(i + 1) != null) {
	                                gridXLocal = Integer.parseInt(mGrid.group(i));
	                                gridYLocal = Integer.parseInt(mGrid.group(i + 1));
	                                break;
	                            }
	                        }
	                    }
	                    
	                 // Inside the line-processing while loop, after detecting coordinators:
	                    Matcher mCoord = coordinatorPattern.matcher(line);
	                    while (mCoord.find()) {
	                        String ensembleOrRoot = mCoord.group(1);
	                        String coordinatorType = mCoord.group(3);

	                        // Add to coordinator set
	                        coorEnsembleNames.add(ensembleOrRoot + "=" + coordinatorType);

	                        // Remove coordinator from componentNames if it was added earlier
	                        componentNames.remove(coordinatorType);  // ensures coordinator is not counted as normal component
	                    }
	                    
                        Matcher matcher = pattern.matcher(line);
                        while (matcher.find()) {
                            String name = matcher.group(1);
                            String type = matcher.group(3);
                            if ("Component".equalsIgnoreCase(type)) {
                                // Only add if it's NOT already registered as a coordinator
                                boolean isCoordinator = coorEnsembleNames.stream()
                                                        .anyMatch(c -> c.endsWith("=" + name));
                                if (!isCoordinator) {
                                    componentNames.add(name);
                                }
                            } else if ("Ensemble".equalsIgnoreCase(type)) {
                                localEnsembles.add(name);
                            } else if ("RootEnsemble".equalsIgnoreCase(type)) {
                                localRootEnsembles.add(name);
                            }
                        }
	                    
	                    Matcher mRole = rolePattern.matcher(line);
	                    while (mRole.find()) localRoles.add(mRole.group(1));

	                    Matcher mState = statePattern.matcher(line);
	                    while (mState.find()) localStates.add(mState.group(1));

	                    Matcher mTrans = transitionPattern.matcher(line);
	                    while (mTrans.find()) {
	                        if (mTrans.group(1) != null) localTransitions.add(mTrans.group(1));
	                        if (mTrans.group(2) != null) localRates.add(mTrans.group(2));
	                    }

	                    Matcher mCond = conditionPattern.matcher(line);
	                    while (mCond.find()) localConditions.add(mCond.group(1) + "=" + mCond.group(2));

	                    Matcher mTrait = traitPattern.matcher(line);
	                    while (mTrait.find()) {
	                        if (mTrait.group(1) != null) localTraits.add(mTrait.group(1));
	                        else if (mTrait.group(2) != null) {
	                            for (String t : mTrait.group(2).split(",")) {
	                                t = t.trim();
	                                if (!t.isEmpty()) localTraits.add(t);
	                            }
	                        }
	                    }

	                    Matcher mPos = positionPattern.matcher(line);
	                    while (mPos.find()) localPositions.add("(" + mPos.group(1) + "," + mPos.group(2) + ")");

	                    Matcher mUtil = utilityPattern.matcher(line);
	                    while (mUtil.find()) localUtility.put(file.getName(), mUtil.group(1));
	                }

	                // === Package results ===
	                result.put("rootEnsembles", localRootEnsembles);
	                result.put("ensembles", localEnsembles);
	                result.put("coordinators", localCoorEnsembleNames);
	                result.put("components", localComponents);
	                result.put("roles", localRoles);
	                result.put("states", localStates);
	                result.put("transitions", localTransitions);
	                result.put("rates", localRates);
	                result.put("conditions", localConditions);
	                result.put("traits", localTraits);
	                result.put("positions", localPositions);
	                result.put("utility", localUtility);
	                result.put("gridX", gridXLocal);
	                result.put("gridY", gridYLocal);
	                return result;
	            }));
	        }

	        executor.shutdown();

	        // ============================= 3. Merge results from all threads =============================
	        int maxGridX = 0, maxGridY = 0;
	        for (Future<Map<String, Object>> future : futures) {
	            Map<String, Object> data = future.get();
	            rootEnsembleNames.addAll((Set<String>) data.get("rootEnsembles"));
	            ensembleNames.addAll((Set<String>) data.get("ensembles"));
	            coorEnsembleNames.addAll((Set<String>) data.getOrDefault("coordinators", new LinkedHashSet<>()));
	            componentNames.addAll((Set<String>) data.get("components"));
	            roleNames.addAll((Set<String>) data.get("roles"));
	            stateNames.addAll((Set<String>) data.get("states"));
	            transitionNames.addAll((Set<String>) data.get("transitions"));
	            transitionRate.addAll((Set<String>) data.get("rates"));
	            conditions.addAll((Set<String>) data.get("conditions"));
	            traitInformation.addAll((Set<String>) data.get("traits"));
	            componentPosition.addAll((Set<String>) data.get("positions"));
	            utilityValue.putAll((Map<String, String>) data.get("utility"));

	            int gx = (int) data.getOrDefault("gridX", 0);
	            int gy = (int) data.getOrDefault("gridY", 0);
	            if (gx > maxGridX) maxGridX = gx;
	            if (gy > maxGridY) maxGridY = gy;
	        }

	        gridX = (maxGridX == 0 ? 10 : maxGridX);
	        gridY = (maxGridY == 0 ? 10 : maxGridY);
	        gridSize = gridX + "x" + gridY;
	        //System.out.println("[INFO] Grid Map Size Detected: " + gridSize);

	        // ============================= 4. Generate and validate XMI =============================
	        savePath = "D:/BACKUP/eclipse-workspace2024/ATLauncherAmrina/ATLauncher/models/generatedXMI_" + System.currentTimeMillis() + ".xmi"; 
	        //xmiContent = XMIUtils.generateXMI(coorEnsembleNames, rootEnsembleNames, ensembleNames, componentNames, transitionNames, transitionRate, traitInformation, roleNames, utilityValue, gridY, gridX, stateNames, conditions, memberships, cardinalities, roleCardinalities, componentPosition);
	        xmiContent = XMIUtilsUpdate.generateXMI(
	        	    coorEnsembleNames, rootEnsembleNames, ensembleNames, componentNames,
	        	    transitionNames, transitionRate, traitInformation, roleNames, utilityValue,
	        	    gridX, gridY,   // ✅ X first, Y second
	        	    stateNames, conditions, memberships, cardinalities, roleCardinalities, componentPosition
	        	);


	        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(savePath)))) {
	            writer.write(xmiContent);
	        }

	        String validated = XMIAutoPipeline.handleGeneratedXMI(savePath);
	        inputTextArea.setText(validated);
	        pathText.setText(savePath);

	        long endXtractNgenerate = System.currentTimeMillis();
	        memEndXtractNgenerate = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
	        elapsedXtractNgenerate = endXtractNgenerate - startXtractNgenerate;
	        memoryXtractNgenerate = memEndXtractNgenerate - memStartXtractNgenerate;

	        System.out.println("========== Extracted Summary ==========");
	        System.out.println("Grid Map Size: " + gridSize);
	        System.out.println("Root Ensemble Names: " + rootEnsembleNames);
	        System.out.println("Ensemble Names: " + ensembleNames);
	        System.out.println("Coordinator Names:" + coorEnsembleNames);
	        System.out.println("Utility Value: " + utilityValue);
	        System.out.println("Component Names: " + componentNames);
	        System.out.println("Position: " + componentPosition);
	        System.out.println("Role Names: " + roleNames);
	        System.out.println("State Names: " + stateNames);
	        System.out.println("Transition Names: " + transitionNames);
	        System.out.println("Transition Rate: " + transitionRate);
	        System.out.println("Conditions: " + conditions);
	        System.out.println("Traits: " + traitInformation);
	        System.out.println("========================================");
	        System.out.printf("[INFO] Extraction completed in %.2f seconds, Memory Used: %.2f MB%n",
	                elapsedXtractNgenerate / 1000.0, memoryXtractNgenerate / (1024.0 * 1024.0));

	    } catch (Exception ex) {
	        ex.printStackTrace();
	        JOptionPane.showMessageDialog(null, "Error: " + ex.getMessage(),
	                "Error", JOptionPane.ERROR_MESSAGE);
	    }
	}

	private static int[] parseGridSize(String s) {
		if (s == null)
			return new int[] { -1, -1 };
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*[xX]\\s*(\\d+)").matcher(s);
		if (m.find()) {
			int gx = Integer.parseInt(m.group(1).trim()); // <-- trim here
			int gy = Integer.parseInt(m.group(2).trim()); // <-- and here
			return new int[] { gx, gy };
		}
		return new int[] { -1, -1 };
	}

	private void extractUtilityFromClass(String className, String classBody, Map<String, String> utilityValue) {
		Pattern utilityPattern = Pattern.compile("utility\\s*\\{(.*?)\\}", Pattern.DOTALL);
		Matcher matcher = utilityPattern.matcher(classBody);
		if (matcher.find()) {
			String utilityContent = matcher.group(1).trim();
			System.out.println("Extracted utility for " + className + ": " + utilityContent); // Debug print
			utilityValue.put(className.trim(), utilityContent);
		} else {
			System.out.println("No utility block found in class: " + className); // Debug print
		}
	}

	private static List<String> computeCoordinatorPrefixes(Set<String> ensembleNames, Set<String> coorEnsembleNames) {
		// Start with whatever your parser explicitly found (may be empty)
		List<String> base = new ArrayList<>();
		if (coorEnsembleNames != null)
			base.addAll(coorEnsembleNames);

		// If empty, infer RootEnsemble names from parsed source (generic; no case-study names)
		// You already push all class names to ensembleNames; ensure you ALSO pushed RootEnsemble classes there.
		if (base.isEmpty() && ensembleNames != null) {
			// Prefer any name that looks like a root (ends with "Coordination", "Coordinator", or "System")
			for (String e : ensembleNames) {
				String n = e.toLowerCase();
				if (n.contains("root") || n.endsWith("coordination") || n.endsWith("coordinator")
						|| "system".equals(n)) {
					base.add(e);
				}
			}
		}

		// Still empty? fall back to the first available ensemble (stable order if LinkedHashSet)
		if (base.isEmpty() && ensembleNames != null && !ensembleNames.isEmpty()) {
			base.add(ensembleNames.iterator().next());
		}

		// Absolute last resort (should never happen, but avoids /0)
		if (base.isEmpty())
			base.add("Root");

		return base;
	}

	protected void clearAll() {// clear All textboxes
		pathText.setText("");
		inputTextArea.setText("");
		outputTextArea.setText("");
		outTextSM.setText("");
		outTextProp.setText("");
		btnGenerate.setVisible(false);
	}

	private String extractGridSizeFromContent(String content) {
		// for (x <- 0 until 10)
		px = Pattern.compile("for\\s*\\(\\s*x\\s*<-\\s*0\\s*until\\s*(\\d+)\\s*\\)");
		// for (y <- 0 until 10)
		py = Pattern.compile("for\\s*\\(\\s*y\\s*<-\\s*0\\s*until\\s*(\\d+)\\s*\\)");
		Matcher mx = px.matcher(content);

		if (mx.find()) {
			int nx = Integer.parseInt(mx.group(1));

			// find y after the x-loop
			Matcher my = py.matcher(content);
			int ny = -1;
			if (my.find(mx.end())) {
				ny = Integer.parseInt(my.group(1));
			} else {
				// fallback: new Array
				arr = Pattern.compile("new\\s+Array\\s*\\[\\s*Node\\s*\\[\\s*MapNodeStatus\\s*]\\s*]\\s*\\(\\s*(\\d+)\\s*\\)");
				Matcher marr = arr.matcher(content);
				if (marr.find(mx.end())) {
					ny = Integer.parseInt(marr.group(1));
				} else {
					ny = nx; // assume square if y not explicit
				}
			}
			return nx + " x " + ny;
		}

		// alternative form: Array.ofDim[Node[MapNodeStatus]](n,m)
		ofDim = Pattern.compile("Array\\s*\\.\\s*ofDim\\s*\\[\\s*Node\\s*\\[\\s*MapNodeStatus\\s*]\\s*]\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");
		Matcher md = ofDim.matcher(content);
		if (md.find()) {
			return md.group(1) + " x " + md.group(2);
		}

		return null;
	}

	// @Override
	protected void verifyPrism() {
		try {
			//Start TOTAL pipeline timer (GUI + PRISM)
	        startVerifyPrism = System.currentTimeMillis();
	        memStartVerifyPrism = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
			
			// 1) Paths
			prismModelPath = "D:/BACKUP/eclipse-workspace2024/ATLauncherAmrina/ATLauncher/models/temp_model.sm";
			prismCommand = "C:\\Program Files\\prism-4.9\\bin\\prism.bat";
			propertyFile = "D:/BACKUP/eclipse-workspace2024/ATLauncherAmrina/ATLauncher/models/temp_properties.props";
			resultFolder = "C:\\Users\\V15\\eclipse-workspace\\ATLauncherMadinah\\ATLauncher\\resultFolder";

			// Try to discover the most recent CTMC XMI you produced: Prefer "outModelPath" (ATL step) or "savePath" (your extractor step)
			if (ctmcXmiPath == null) {
				if (outModelPath != null && new File(outModelPath).isFile()) {
					ctmcXmiPath = outModelPath;
				} else if (savePath != null && new File(savePath).isFile()) {
					ctmcXmiPath = savePath;
				}
			}

			// 2) Ensure we have content to run
			saveTextToFile(outTextSM.getText(), prismModelPath);
			saveTextToFile(outTextProp.getText(), propertyFile);

			file = new File(prismModelPath);
			if (file.length() == 0) {
				JOptionPane.showMessageDialog(null, "PRISM model file is empty! Please check your output.", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			// Optional: display the saved files in your side panes (you already do)
			displayPrismFileContent(prismModelPath, outTextSM);
			displayPrismFileContent(propertyFile, outTextProp);

			// 3) Create a run folder (e.g., out/2025-09-01_10-22-10)
			if (runId == null)
				runId = PrismEvalExport.newRunId();
			java.nio.file.Path runDir = PrismEvalExport.runDir("resultFolder", runId);

			// Identify scenario for charts/CSVs (use a UI widget later if you have one)
			String scenarioId = currentScenarioName(); // e.g., "EASY"
			String modelName = new File(prismModelPath).getName();

			// 4) Run PRISM
			ProcessBuilder pb = new ProcessBuilder(prismCommand, prismModelPath, propertyFile);
			pb.directory(new File("C:\\Program Files\\prism-4.9\\bin"));
			pb.redirectErrorStream(true);

			process = pb.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			StringBuilder output = new StringBuilder(4096);
			String line, prismVersion = "unknown";
			boolean parseOK = false;
			String parseErr = "";

			// ⭐ PRISM-only timer (just the model checking)
	        long prismStart = System.nanoTime();
	        
			while ((line = reader.readLine()) != null) {
				output.append(line).append("\n");
				if ("unknown".equals(prismVersion) && line.toLowerCase().contains("prism version")) {
					prismVersion = line.trim();
				}
				if (line.toLowerCase().contains("model checking:"))
					parseOK = true;
				if (line.toLowerCase().contains("error"))
					parseErr = line.trim();
			}
			
			long prismEnd = System.nanoTime();
	        double prismOnlyTimeSec = (prismEnd - prismStart) / 1e9;   // ⭐ PRISM-only time (sec)

			// 5) Show PRISM stdout window (your existing utility)
			showPrismOutputInFrame(output.toString());

			// 6) Exports (toggled)
			if (exportBar.cbEnv.isSelected()) {
				PrismEvalExport.exportEnvironment(runDir, prismVersion); // R1
			}
			if (exportBar.cbParse.isSelected()) {
				PrismEvalExport.logParseStatus(runDir, scenarioId, modelName, prismVersion, parseOK, parseErr); // T1
			}

			// 7) Compute metrics: prefer CTMC XMI, fallback to .sm
			long states = -1, transitions = -1;
			int numAgents = -1, numTraits = -1;

			if (ctmcXmiPath != null && new File(ctmcXmiPath).isFile()) {
				try {
					CTMCXmiMetrics.Config cfg = new CTMCXmiMetrics.Config();
					// If your metamodel uses different element names, update cfg.stateTags / cfg.transitionTags
					CTMCXmiMetrics.Result r = CTMCXmiMetrics.analyze(new File(ctmcXmiPath), cfg);
					states = r.states;
					transitions = r.transitions;
					numAgents = r.agents;
					numTraits = r.traits;
				} catch (Exception ex) {
					System.err.println("XMI metrics failed: " + ex.getMessage());
				}
			}
			if (states < 0 || transitions < 0) { // fallback
				try {
					SmHeuristicMetrics.Result hr = SmHeuristicMetrics.analyze(java.nio.file.Paths.get(prismModelPath));
					states = hr.statesLowerBound;
					transitions = hr.transitions;
				} catch (Exception ex) {
					System.err.println("SM heuristic metrics failed: " + ex.getMessage());
				}
			}

			double genTimeSec = estimateGenerationTimeFromYourPipeline();
	        if (exportBar.cbMetrics.isSelected()) {
	            long memMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024L * 1024L);
	            PrismEvalExport.logScenarioMetrics(
	                    runDir, scenarioId, modelName,
	                    numAgents, numTraits, states, transitions,
	                    genTimeSec, prismOnlyTimeSec, memMB
	            ); // T3
	        }

			// ⭐ Stop TOTAL pipeline timer (GUI + PRISM)
	        endVerifyPrism = System.currentTimeMillis();
	        memEndVerifyPrism = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
	        double pipelineTimeSec = (endVerifyPrism - startVerifyPrism) / 1000.0;

	        // 8) Dashboards + T4 property outcome export
	        ShowPrismGraphWindow.Parsed parsed =
	                ShowPrismGraphWindow.plotComparisonsFromPrismLog(
	                        output.toString(), runDir, prismOnlyTimeSec,    // PRISM-only time
	                        pipelineTimeSec      // total pipeline time
	                ); // F1
		} catch (Exception ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(null, "Failed to run PRISM: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	// Method to display PRISM output in a resizable, scrollable frame with padding
	private void showPrismOutputInFrame(String output) {
		JFrame outputFrame = new JFrame("PRISM Execution Result");
		outputFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		outputFrame.setSize(800, 600); // Initial size
		outputFrame.setLocationRelativeTo(null); // Center the window

		JTextArea outputArea = new JTextArea(output);
		outputArea.setLineWrap(true);
		outputArea.setWrapStyleWord(true);
		outputArea.setEditable(false);

		// Add padding around the text area
		outputArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JScrollPane scrollPane = new JScrollPane(outputArea);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		// Add scroll pane to a panel with padding
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Add padding to the panel
		panel.add(scrollPane, BorderLayout.CENTER);

		outputFrame.getContentPane().add(panel);
		outputFrame.setVisible(true);
	}

	private void showPrismGraphWindow(String output) {
		List<Integer> xData = new ArrayList<>();
		List<Double> yData = new ArrayList<>();

		// Parse output
		String[] lines = output.split("\\R"); // handles all line endings
		int index = 1;
		String graphTitle = "PRISM Results";
		for (String line : lines) {
			// Try to extract filename from a line like "Model checking: fire_easy.sm"
			if (line.toLowerCase().contains("model checking:")) {
				int colonIndex = line.indexOf(":");
				if (colonIndex != -1 && colonIndex + 1 < line.length()) {
					graphTitle = line.substring(colonIndex + 1).trim();
				}
			}

			// Extract probability result
			if (line.toLowerCase().contains("result") && line.contains(":")) {
				try {
					String[] parts = line.split(":");
					double val = Double.parseDouble(parts[1].trim());
					xData.add(index++);
					yData.add(val);
				} catch (Exception ignored) {
				}
			}
		}

		// Add Null & Empty Check Before Drawing Graph
		if (yData.isEmpty()) {
			JOptionPane.showMessageDialog(null, "No PRISM 'Result' lines found to plot.", "Graph Error", JOptionPane.WARNING_MESSAGE);
			return;
		}

		// Build the chart
		XYChart chart = new XYChartBuilder().width(800).height(500).title("PRISM Probability Results")
				.xAxisTitle("Scenario Index").yAxisTitle("Probability").build();

		chart.getStyler().setLegendVisible(false);
		chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
		chart.getStyler().setMarkerSize(8);
		chart.getStyler().setChartTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
		chart.addSeries("Result", xData, yData);

		// Display in a separate frame
		JFrame chartFrame = new JFrame("PRISM Result Chart");
		chartFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		chartFrame.setSize(900, 600);
		chartFrame.setLocationRelativeTo(null);

		JPanel chartPanel = new XChartPanel<>(chart);
		chartFrame.getContentPane().add(chartPanel);
		chartFrame.setVisible(true);
	}

	private void displayPrismFileContent(String filePath, JTextArea textArea) {
		try {
			File file = new File(filePath);
			BufferedReader reader = new BufferedReader(new FileReader(file));
			StringBuilder content = new StringBuilder();
			String line;

			while ((line = reader.readLine()) != null) {
				content.append(line).append("\n");
			}

			reader.close();
			System.out.println(content.toString());// Display content in the JTextArea

		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "Error reading PRISM file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void printSummaryStats() {
		System.out.println("===== SUMMARY STATS =====");
		System.out.println("Time to generate XMI: " + elapsedXtractNgenerate + " ms");
		System.out.println("Memory used when generate XMI: " + memoryXtractNgenerate + " KB");

		long convertTime = endConvert - startConvert;
		long memoryConvertUsed = (memEndConvert - memStartConvert) / 1024;
		System.out.println("Time for XMI to XMI (ATL) conversion time: " + convertTime + " ms");
		System.out.println("Memory used when XMI to XMI (ATL) conversion time: " + memoryConvertUsed + " KB");

		convertPRISMTime = endConvertPrismModel - startConvertPrismModel;
		memoryConvertPRISMused = (memConvertEndPrismModel - memConvertStartPrismModel) / 1024;
		System.out.println("Convert PRISM model generation time: " + (endConvertPrismModel - startConvertPrismModel) + " ms");
		System.out.println("Memory used when convert PRISM model generation time: "+ ((memConvertEndPrismModel - memConvertStartPrismModel) / 1024) + " KB");

		convertPropsTime = endConvertPrismModel - startConvertPrismModel;
		memoryConvertPropsused = (memEndConvertProps - memStartConvertProps) / 1024;
		System.out.println("PRISM properties generation time: " + (endConvertProps - startConvertProps) + " ms");
		System.out.println("Memory used when PRISM properties generation time: " + ((memEndConvertProps - memStartConvertProps) / 1024) + " KB");

		long OverallConvertPrismTime = endGeneratePrism - startGeneratePrism;
		long OverallConvertPrismMemUsed = (memEndGeneratePrism - memStartGeneratePrism) / 1024;
		System.out.println("Time for XMI to SM conversion time: " + OverallConvertPrismTime + " ms");
		System.out.println("Memory used when XMI to SM conversion time: " + OverallConvertPrismMemUsed + " KB");
		
		double verifyTimeMs  = (verifyEnd - verifyStart) / 1e6;
		System.out.println("PRISM-only model verification time: " + verifyTimeMs + " ms");

		long verifyTime = endVerifyPrism - startVerifyPrism;
		long verifyMemUsed = (memEndVerifyPrism - memStartVerifyPrism) / 1024;
		System.out.println("Total verification pipeline time (GUI + PRISM) : " + verifyTime + " ms");
		System.out.println("Memory used when PRISM verification time: " + verifyMemUsed + " KB");
		System.out.println("==========================");
	}

	private String currentScenarioName() {
		// TODO: wire to your UI later (combo/radio). For now:
		return "EASY";
	}

	private double estimateGenerationTimeFromYourPipeline() {
		// Ideally: measure your ATL/EMFTVM transformation time and return it here.
		// For now, we can’t measure it in this method, so:
		return -1.0;
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MainFrame frame = new MainFrame();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
}