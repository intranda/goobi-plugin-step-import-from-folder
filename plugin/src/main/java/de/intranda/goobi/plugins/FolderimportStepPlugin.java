package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.UGHException;

@PluginImplementation
@Log4j2
public class FolderimportStepPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_folderimport";
    @Getter
    private Step step;

    private Process process;
    private Prefs prefs;

    // doc type configuration
    private List<StringPair> prefixList = new ArrayList<>();
    private String mainType;
    private List<StringPair> suffixList = new ArrayList<>();

    // root folder for images
    @Setter // setter for junit test, call it after initialize
    private String rootFolder;

    private MetadataType titleType;

    @Override
    public void initialize(Step step, String returnPath) {
        this.step = step;
        process = step.getProzess();
        prefs = process.getRegelsatz().getPreferences();
        // read parameters from correct block in configuration file
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        myconfig.setExpressionEngine(new XPathExpressionEngine());
        rootFolder = myconfig.getString("/imageFolder");

        List<HierarchicalConfiguration> pl = myconfig.configurationsAt("/prefixType");
        for (HierarchicalConfiguration hc : pl) {
            String folderName = hc.getString("@foldername");
            String doctype = hc.getString("@doctype");
            prefixList.add(new StringPair(folderName, doctype));
        }

        mainType = myconfig.getString("mainType");

        List<HierarchicalConfiguration> sl = myconfig.configurationsAt("/suffixType");
        for (HierarchicalConfiguration hc : sl) {
            String folderName = hc.getString("@foldername");
            String doctype = hc.getString("@doctype");
            suffixList.add(new StringPair(folderName, doctype));
        }
        titleType = prefs.getMetadataTypeByName("TitleDocMain");

    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return "/uii/plugin_step_folderimport.xhtml";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "";
    }

    @Override
    public String finish() {
        return "";
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {
        Fileformat fileformat = null;
        DigitalDocument dd = null;
        DocStruct logical = null;
        DocStruct physical = null;
        // open metadata file
        try {
            fileformat = process.readMetadataFile();
            dd = fileformat.getDigitalDocument();
            logical = dd.getLogicalDocStruct();
            if (logical.getType().isAnchor()) {
                logical = logical.getAllChildren().get(0);
            }
            physical = dd.getPhysicalDocStruct();

        } catch (UGHException | IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
            Helper.setFehlerMeldung("Metadata not readable");
            return PluginReturnValue.ERROR;
        }

        // get main title
        List<? extends Metadata> titles = logical.getAllMetadataByType(titleType);
        if (titles.isEmpty()) {
            log.error("No main title found for {}", process.getId());
            Helper.setFehlerMeldung("No main title found.");
            return PluginReturnValue.ERROR;
        }
        String mainTitle = titles.get(0).getValue();

        // search for folder
        Path folder = Paths.get(rootFolder, mainTitle);

        // if folder doesn't exist -> error
        if (!StorageProvider.getInstance().isDirectory(folder)) {
            log.error("No folder to import found: {}", folder);
            Helper.setFehlerMeldung("No folder to import found.");
            return PluginReturnValue.ERROR;
        }

        // read content of folder



        // get correct prefix items from configuration file (like Vorderdeckel, Titelblatt,  Buchrücken)

        // get correct suffix items from configuration file (like Buchrücken, Frabkeil, ...)

        // assign folder to prefix and suffix items

        // order other folder

        // create structure elements

        // create page elements

        // copy/move images

        // save

        //BandSerie/Akte/
        //Deckblatt
        //Titelblatt
        //Vorgang* -> Titel generieren "Protokoll vom " +  Date
        // PublicationYear + Dating generieren

        boolean successfull = true;
        // your logic goes here

        log.info("Folderimport step plugin executed");
        if (!successfull) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }
}
