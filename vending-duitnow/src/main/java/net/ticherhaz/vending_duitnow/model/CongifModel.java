package net.ticherhaz.vending_duitnow.model;

import java.io.Serializable;

public class CongifModel implements Serializable {

    public int id;
    public String merchantcode;
    public String merchantkey;
    public String ipforpaywave;
    public String fid;
    public String mid;
    public String toopid;
    public String tooppass;


    public CongifModel() {
    }

    public CongifModel(String merchantcode, String merchantkey, String ipforpaywave, String fid, String mid, String toopid, String tooppass) {
        this.merchantcode = merchantcode;
        this.merchantkey = merchantkey;
        this.ipforpaywave = ipforpaywave;
        this.fid = fid;
        this.mid = mid;
        this.toopid = toopid;
        this.tooppass = tooppass;
    }

    public CongifModel(int id, String merchantcode, String merchantkey, String ipforpaywave, String fid, String mid, String toopid, String tooppass) {
        this.id = id;
        this.merchantcode = merchantcode;
        this.merchantkey = merchantkey;
        this.ipforpaywave = ipforpaywave;
        this.fid = fid;
        this.mid = mid;
        this.toopid = toopid;
        this.tooppass = tooppass;
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getMerchantcode() {
        return merchantcode;
    }

    public void setMerchantcode(String merchantcode) {
        this.merchantcode = merchantcode;
    }

    public String getMerchantkey() {
        return merchantkey;
    }

    public void setMerchantkey(String merchantkey) {
        this.merchantkey = merchantkey;
    }

    public String getIpforpaywave() {
        return ipforpaywave;
    }

    public void setIpforpaywave(String ipforpaywave) {
        this.ipforpaywave = ipforpaywave;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getMid() {
        return mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }

    public String getToopid() {
        return toopid;
    }

    public void setToopid(String toopid) {
        this.toopid = toopid;
    }

    public String getTooppass() {
        return tooppass;
    }

    public void setTooppass(String tooppass) {
        this.tooppass = tooppass;
    }
}
