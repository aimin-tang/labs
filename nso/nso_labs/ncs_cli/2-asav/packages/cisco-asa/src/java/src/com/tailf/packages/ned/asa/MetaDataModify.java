package com.tailf.packages.ned.asa;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.tailf.ned.NedWorker;
import com.tailf.ned.NedException;

import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfValue;

import com.tailf.maapi.Maapi;


// META SYNTAX:
// ================================
// metas[0] = ! meta-data
// metas[1] = path
// metas[2] = annotation name
// metas[3..N] = meta value(s)

//
// Supported meta annotations:
//   context-config-url
//   context-delete


/**
 * Utility class for modifying config data based on YANG model meta data provided by NCS.
 *
 * @author lbang
 * @version 20170917
 */
@SuppressWarnings("deprecation")
public class MetaDataModify {

    /*
     * Local data
     */
    private String device_id;
    private String model;
    private boolean isNetsim;
    private boolean trace;
    private boolean logVerbose;
    private boolean autoConfigUrlFileDelete;

    /**
     * Constructor
     */
    MetaDataModify(String device_id, String model, boolean trace, boolean logVerbose,
                   boolean autoConfigUrlFileDelete) {
        this.device_id  = device_id;
        this.model      = model;
        this.trace      = trace;
        this.logVerbose = logVerbose;
        this.autoConfigUrlFileDelete = autoConfigUrlFileDelete;
        this.isNetsim = model.equals("NETSIM");
    }

    /*
     * Write info in NED trace
     *
     * @param info - log string
     */
    private void traceInfo(NedWorker worker, String info) {
        if (trace) {
            worker.trace("-- " + info + "\n", "out", device_id);
        }
    }

    /*
     * Write info in NED trace if verbose output
     *
     * @param info - log string
     */
    private void traceVerbose(NedWorker worker, String info) {
        if (logVerbose && trace) {
            worker.trace("-- " + info + "\n", "out", device_id);
        }
    }

    private String getMatch(String text, String regexp) {
        Pattern pattern = Pattern.compile(regexp);
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find())
            return null;
        return matcher.group(1);
    }

    private int getCmd(String lines[], int i) {
        for (int cmd = i; cmd < lines.length; cmd++) {
            String trimmed = lines[cmd].trim();
            if (trimmed.isEmpty())
                continue;
            if (trimmed.startsWith("! meta-data :: /ncs:devices/device{"))
                continue;
            return cmd;
        }
        return -1;
    }

    /*
     * maapiExists
     */
    private boolean maapiExists(NedWorker worker, Maapi mm, int th, String path)
        throws NedException {
        try {
            if (mm.exists(th, path)) {
                traceVerbose(worker, "maapiExists("+path+") = true");
                return true;
            }
        } catch (Exception e) {
            throw new NedException("maapiExists("+path+") ERROR : " + e.getMessage());
        }

        traceVerbose(worker, "maapiExists("+path+") = false");
        return false;
    }

    /**
     * Modify config data based on meta-data given by NCS.
     *
     * @param data - config data from applyConfig, before commit
     * @return Config data modified after parsing !meta-data tags
     */
    public String modifyData(NedWorker worker, String data, Maapi mm, int fromTh, int toTh)
        throws NedException {

        String lines[] = data.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().isEmpty())
                continue;
            if (lines[i].trim().startsWith("! meta-data :: /ncs:devices/device{") == false) {
                sb.append(lines[i] + "\n");  // Normal config line -> add
                continue;
            }

            // Find command index (reason: can be multiple meta-data tags per command)
            int cmd = getCmd(lines, i + 1);
            if (cmd == -1) {
                continue;
            }
            String line = lines[cmd];
            String trimmed = lines[cmd].trim();
            String transformed = null;

            // Extract meta-data and meta-value(s), store in metas[] where:
            // metas[1] = meta path
            // metas[2] = meta tag name
            // metas[3] = first meta-value (each value separated by ' :: '
            String meta = lines[i].trim();
            String metas[] = meta.split(" :: ");
            String metaPath = metas[1];
            String metaTag = metas[2];


            // context-config-url
            // ==================
            // Delete context file on disk when config-url is set
            if (metaTag.equals("context-config-url")) {
                if (isNetsim || line.startsWith(" config-url") == false
                    || autoConfigUrlFileDelete == false)
                    continue;
                String filename = getMatch(line, "config-url[ ]+(\\S+)");
                String delcmd = "delete /noconfirm "+filename;
                traceInfo(worker, "meta-data "+metaTag+" :: transformed => injected '"+delcmd+"'");
                sb.append(delcmd + "\n");
                continue;
            }

            // context-delete
            // ==============
            // Delete context file on disk when context is deleted
            else if (metaTag.equals("context-delete")) {
                if (isNetsim || line.startsWith("no context ") == false)
                    continue;
                String urlpath = metaPath+"/config-url";
                traceVerbose(worker, "URLPATH="+urlpath);
                try {
                    if (maapiExists(worker, mm, fromTh, urlpath)) {
                        String filename = ConfValue.getStringByValue(urlpath, mm.getElem(fromTh, urlpath));
                        String delcmd = "delete /noconfirm "+filename;
                        traceInfo(worker, "meta-data "+metaTag+" :: transformed => injected '"+delcmd+"'");
                        sb.append(lines[cmd] + "\n"); // Add 'no context ..'
                        lines[cmd] = delcmd; // Add 'delete ..'
                    } else {
                        traceInfo(worker, "meta-data "+metaTag+" WARNING missing "+urlpath);
                    }
                } catch (Exception ignore) {}
                continue;
            }

            //
            // Simple transformations -> log and change transformed line 'lines[cmd]'
            //
            if (transformed != null && !transformed.equals(lines[cmd])) {
                if (transformed.isEmpty())
                    traceInfo(worker, "meta-data "+metaTag+" :: transformed => stripped '"+trimmed+"'");
                else
                    traceInfo(worker, "meta-data "+metaTag+" :: transformed => '"+trimmed+"' to '"+transformed+"'");
                lines[cmd] = transformed;
                // note: meta tag is discarded automatically here since not added
            }

            // metaTag not handled by this loop -> copy it over
            else {
                sb.append(lines[i] + "\n");
            }
        }
        data = "\n" + sb.toString() + "\n";

        return data;
    }
}
