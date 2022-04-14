package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;

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
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
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
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.UGHException;
import ugh.exceptions.WriteException;

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
    private MetadataType datingType;
    private MetadataType publicationType;

    private DocStructType pageType;
    private MetadataType physType;
    private MetadataType logType;

    private String masterFolder;

    @Override
    public void initialize(Step step, String returnPath) {
        this.step = step;
        process = step.getProzess();
        prefs = process.getRegelsatz().getPreferences();
        // read parameters from correct block in configuration file
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        rootFolder = myconfig.getString("/imageFolder");

        List<HierarchicalConfiguration> pl = myconfig.configurationsAt("/prefixType");
        for (HierarchicalConfiguration hc : pl) {
            String doctype = hc.getString("@doctype");
            String folderName = hc.getString("@foldername");
            prefixList.add(new StringPair(folderName, doctype));
        }

        mainType = myconfig.getString("/mainType");

        List<HierarchicalConfiguration> sl = myconfig.configurationsAt("/suffixType");
        for (HierarchicalConfiguration hc : sl) {
            String folderName = hc.getString("@foldername");
            String doctype = hc.getString("@doctype");
            suffixList.add(new StringPair(folderName, doctype));
        }
        titleType = prefs.getMetadataTypeByName("TitleDocMain");
        datingType = prefs.getMetadataTypeByName("Dating");
        publicationType = prefs.getMetadataTypeByName("PublicationYear");
        pageType = prefs.getDocStrctTypeByName("page");
        physType = prefs.getMetadataTypeByName("physPageNumber");
        logType = prefs.getMetadataTypeByName("logicalPageNumber");
        try {
            masterFolder = process.getImagesOrigDirectory(false);
        } catch (IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
        }
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

        // Tile: "Konsulatsprotokolle 1636 - 1638"

        String titlePrefix = mainTitle.substring(0, mainTitle.lastIndexOf("-")).trim();
        // -> "Konsulatsprotokolle 1636"
        // Folder: Konsulatsprotokolle 1636-01-21 - 1638-04-17
        // search for folder
        List<Path> allFolder = StorageProvider.getInstance().listFiles(rootFolder);
        Path folder = null;
        for (Path current : allFolder) {
            if (current.getFileName().toString().startsWith(titlePrefix)) {
                folder = current;
                break;
            }
        }

        // if folder doesn't exist -> error
        if (folder == null || !StorageProvider.getInstance().isDirectory(folder)) {
            log.error("No folder to import found: {}", folder);
            Helper.setFehlerMeldung("No folder to import found.");
            return PluginReturnValue.ERROR;
        }

        // read content of folder

        List<String> allFolderInDirectory = StorageProvider.getInstance().list(folder.toString());

        List<String> prefixFolder = new ArrayList<>();
        List<String> suffixFolder = new ArrayList<>();
        List<String> otherFolder = new ArrayList<>();

        // get correct prefix items from configuration file
        for (StringPair sp : prefixList) {
            String folderName = sp.getOne();
            for (String currentFolder : allFolderInDirectory) {
                if (currentFolder.equalsIgnoreCase(folderName)) {
                    prefixFolder.add(currentFolder);
                }
            }
        }

        // get correct suffix items from configuration file (like Buchrücken, Frabkeil, ...)

        for (StringPair sp : suffixList) {
            String folderName = sp.getOne();
            for (String currentFolder : allFolderInDirectory) {
                if (currentFolder.equalsIgnoreCase(folderName)) {
                    suffixFolder.add(currentFolder);
                }
            }
        }

        // order other folder
        for (String currentFolder : allFolderInDirectory) {
            if (!prefixFolder.contains(currentFolder) && !suffixFolder.contains(currentFolder)) {
                otherFolder.add(currentFolder);
            }
        }
        Collections.sort(otherFolder);
        int imageIndex = 1;

        // create all structure elements

        // create cover, title page, ...
        for (String foldername : prefixFolder) {
            String docstructName = null;
            for (StringPair sp : prefixList) {
                if (foldername.equalsIgnoreCase(sp.getOne())) {
                    docstructName = sp.getTwo();
                    break;
                }
            }
            try {
                imageIndex = createDocstruct(dd, logical, physical, folder, foldername, docstructName, imageIndex, false);
            } catch (UGHException | IOException e) {
                log.error(e);
            }
        }
        // create main elements
        for (String currentFolder : otherFolder) {
            try {
                // create metadata TitleDocMain,  PublicationYear, Dating
                imageIndex = createDocstruct(dd, logical, physical, folder, currentFolder, mainType, imageIndex, true);
            } catch (UGHException | IOException e) {
                log.error(e);
            }
        }

        // create end elements
        for (String foldername : suffixFolder) {
            String docstructName = null;
            for (StringPair sp : suffixList) {
                if (foldername.equalsIgnoreCase(sp.getOne())) {
                    docstructName = sp.getTwo();
                    break;
                }
            }
            try {
                imageIndex = createDocstruct(dd, logical, physical, folder, foldername, docstructName, imageIndex, false);
            } catch (UGHException | IOException e) {
                log.error(e);
            }
        }
        // save
        try {
            process.writeMetadataFile(fileformat);
        } catch (WriteException | PreferencesException | IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
        }

        return PluginReturnValue.FINISH;
    }

    private int createDocstruct(DigitalDocument dd, DocStruct logical, DocStruct physical, Path mainFolder, String imageFolder, String docstructName,
            int imageIndex, boolean createMetadata)
                    throws TypeNotAllowedForParentException, TypeNotAllowedAsChildException, MetadataTypeNotAllowedException, IOException {
        // create docstruct
        DocStruct ds = dd.createDocStruct(prefs.getDocStrctTypeByName(docstructName));
        logical.addChild(ds);

        // create page elements
        List<Path> imagesInFolder = StorageProvider.getInstance().listFiles(Paths.get(mainFolder.toString(), imageFolder).toString());
        for (Path image : imagesInFolder) {
            String newImageName = imageFolder + "_" + image.getFileName().toString();
            if (newImageName.endsWith("Thumbs.db")) {
                continue;
            }
            newImageName = newImageName.replaceAll("[^\\w_\\.]", "_");

            DocStruct dsPage = dd.createDocStruct(pageType);

            ContentFile cf = new ContentFile();
            if (SystemUtils.IS_OS_WINDOWS) {
                cf.setLocation("file:/" + Paths.get(masterFolder, newImageName).toString());
            } else {
                cf.setLocation("file://" + Paths.get(masterFolder, newImageName).toString());
            }
            dsPage.addContentFile(cf);
            physical.addChild(dsPage);
            // assign pages to ds and logical

            Metadata mdLogicalPageNo = new Metadata(logType);
            dsPage.addMetadata(mdLogicalPageNo);

            Metadata mdPhysPageNo = new Metadata(physType);
            mdPhysPageNo.setValue(String.valueOf(imageIndex));
            dsPage.addMetadata(mdPhysPageNo);
            logical.addReferenceTo(dsPage, "logical_physical");
            ds.addReferenceTo(dsPage, "logical_physical");
            imageIndex = imageIndex + 1;
            if (createMetadata) {
                try {
                    if (titleType != null) {
                        Metadata title = null;
                        if (ds.getAllMetadataByType(titleType).isEmpty()) {
                            title = new Metadata(titleType);
                            ds.addMetadata(title);
                        } else {
                            title = ds.getAllMetadataByType(titleType).get(0);
                        }
                        title.setValue("Protokoll vom " + imageFolder);
                    }
                } catch (Exception e) {
                    log.error(e);
                }
                String date = imageFolder.split(";")[0];
                if (StringUtils.isNotBlank(date)) {
                    try {
                        if (publicationType != null) {
                            Metadata md = new Metadata(publicationType);
                            md.setValue(date);
                            ds.addMetadata(md);
                        }
                        if (datingType != null) {
                            Metadata md = new Metadata(datingType);
                            md.setValue(date);
                            ds.addMetadata(md);
                        }
                    } catch (Exception e) {
                        log.error(e);
                    }
                }

            }

            // rename and copy image

            Path destination = Paths.get(masterFolder, newImageName);
            StorageProvider.getInstance().copyFile(image, destination);

        }
        return imageIndex;
    }
}
