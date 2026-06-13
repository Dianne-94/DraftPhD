package mainCodingFiles;

import javax.swing.*;
import java.awt.*;

public class PrismExportBar extends JPanel {
    public JCheckBox cbEnv    = new JCheckBox("Export Environment (R1)");
    public JCheckBox cbParse  = new JCheckBox("Parse Status (T1)");
    public JCheckBox cbTrace  = new JCheckBox("Traceability (T2)");
    public JCheckBox cbMetrics= new JCheckBox("Scenario Metrics (T3)");
    public JCheckBox cbProps  = new JCheckBox("Property Outcomes (T4)");
    public JCheckBox cbScaling= new JCheckBox("Scaling Plots (F2)");
    
    public PrismExportBar() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        setBorder(BorderFactory.createTitledBorder("Evaluation Export Options")); // <-- Label here

        add(cbEnv);
        add(cbParse);
        add(cbTrace);
        add(cbMetrics);
        add(cbProps);
        add(cbScaling);

        // Defaults (tick them if you want)
        cbEnv.setSelected(true);
        cbParse.setSelected(true);
        cbTrace.setSelected(true);
        cbMetrics.setSelected(true);
        cbProps.setSelected(true);
        cbScaling.setSelected(true);
    }
}