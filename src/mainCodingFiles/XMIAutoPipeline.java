package mainCodingFiles;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.JOptionPane;

/* XMIAutoPipeline:
 * - Validates headers (via XMIValidator)
 * - Sanitizes conditions (via ConditionSanitizer)
 * - Supports .xmi and .txt input
 * - Produces cleaned XMI for ATL and PrismGenerator
 */
public class XMIAutoPipeline {

    /** Handle user-uploaded XMI/TXT files */
    public static String handleUserInput(File file) {
        String fileName = file.getName().toLowerCase();

        try {
            if (fileName.endsWith(".xmi")) {
                System.out.println("Validating uploaded XMI file: " + fileName);

              //String validated = XMIValidator.validateAndFix(file);
                String validated = XMIValidatorUpdated17022026.validateAndFix(file);

                // ⭐ Sanitize conditions (range/numeric/qualitative)
                validated = ConditionSanitizer.sanitize(validated);

                System.out.println("XMI validation + sanitization complete.");
                return validated;
            }

            else if (fileName.endsWith(".txt")) {
                System.out.println("Loading TXT input: " + fileName);
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                return content;
            }

            else {
                JOptionPane.showMessageDialog(null,
                        "Unsupported file type: " + fileName,
                        "File Error", JOptionPane.ERROR_MESSAGE);
                return "";
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Error processing file: " + e.getMessage(),
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            return "";
        }
    }

    /** Handle system-generated XMI from xtractNgenerate() */
    public static String handleGeneratedXMI(String xmiPath) {
        try {
            File file = new File(xmiPath);
            if (!file.exists()) {
                JOptionPane.showMessageDialog(null,
                        "Generated XMI file not found at: " + xmiPath,
                        "File Not Found", JOptionPane.ERROR_MESSAGE);
                return "";
            }

            System.out.println("Validating generated XMI: " + file.getName());
            //String validated = XMIValidator.validateAndFix(file);
            String validated = XMIValidatorUpdated17022026.validateAndFix(file);

            // ⭐ Sanitize (range/numeric/qualitative)
            validated = ConditionSanitizer.sanitize(validated);

            System.out.println("Generated XMI validation + sanitization successful.");
            return validated;

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Error validating generated XMI: " + e.getMessage(),
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            return "";
        }
    }
}
