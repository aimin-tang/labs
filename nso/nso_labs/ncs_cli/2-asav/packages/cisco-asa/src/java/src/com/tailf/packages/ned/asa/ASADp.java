package com.tailf.packages.ned.asa;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.regex.Pattern;

import com.tailf.maapi.Maapi;
import com.tailf.ncs.NcsMain;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.ns.Ncs;

import com.tailf.conf.Conf;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfValue;
import com.tailf.maapi.MaapiConfigFlag;
import com.tailf.maapi.MaapiSchemas;
import com.tailf.maapi.MaapiSchemas.CSNode;

import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuList;

import com.tailf.dp.DpCallbackException;
import com.tailf.dp.DpTrans;
import com.tailf.dp.DpUserInfo;
import com.tailf.dp.annotations.DataCallback;
import com.tailf.dp.annotations.TransCallback;
import com.tailf.dp.proto.DataCBType;
import com.tailf.dp.proto.TransCBType;

public class ASADp {

    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE)
    public Maapi mm;

    private boolean isNetconf(DpTrans trans)
        throws DpCallbackException {

        DpUserInfo uinfo = trans.getUserInfo();
        if ("netconf".equals(uinfo.getContext()))
            return true;

        return false;
    }

    // contextRemove
    @DataCallback(callPoint="context-hook",
                  callType=DataCBType.REMOVE)
        public int contextRemove(DpTrans trans, ConfObject[] keyPath)
        throws DpCallbackException {
        try {
            if (isNetconf(trans))
                return Conf.REPLY_OK;

            int tid = trans.getTransaction();
            String path = new ConfPath(keyPath).toString();
            String ctxpath = path.replace("asa:context", "asa:changeto/context");

            //System.out.println("contextRemove() path="+path+" ctxpath="+ctxpath);
            mm.safeDelete(tid, ctxpath);

            return Conf.REPLY_OK;
        }
        catch (Exception e) {
            throw new DpCallbackException("", e);
        }
    }

    // ASADpInit
    @TransCallback(callType=TransCBType.INIT)
    public void ASADpInit(DpTrans trans) throws DpCallbackException {

        try {
            if (mm == null) {
                // Need a Maapi socket so that we can attach
                Socket s = new Socket("127.0.0.1", NcsMain.getInstance().
                                      getNcsPort());
                mm = new Maapi(s);
            }
            mm.attach(trans.getTransaction(),0,
                      trans.getUserInfo().getUserId());
            return;
        }
        catch (Exception e) {
            throw new DpCallbackException("Failed to attach", e);
        }
    }


    // ASADpFinish
    @TransCallback(callType=TransCBType.FINISH)
    public void ASADpFinish(DpTrans trans) throws DpCallbackException {

        try {
            mm.detach(trans.getTransaction());
        }
        catch (Exception e) {
            ;
        }
    }

}
