package net.ticherhaz.vending_duitnow.model;

import java.util.List;

public class UserObj {
    public String ipaytype;
    public double chargingprice;
    public double promoamt;
    public String promname;
    public boolean isloggedin;
    public double points;
    public int userid;
    public int pid;
    public String expiredate;
    public int userstatus;
    public List<CartListModel> cartModel;
    public List<CongifModel> configModel;
    public int image;
    public String mtd;

    public int getImage() {
        return image;
    }

    public void setImage(int image1) {
        image = image1;
    }

    public boolean getIsloggedin() {
        return isloggedin;
    }

    public void setIsloggedin(boolean isloggedin1) {
        isloggedin = isloggedin1;
    }

    public List<CartListModel> getCartModel() {
        return cartModel;
    }

    public void setCartModel(List<CartListModel> cartModel1) {
        cartModel = cartModel1;
    }

    public List<CongifModel> getConfigModel() {
        return configModel;
    }

    public void setConfigModel(List<CongifModel> configModel1) {
        configModel = configModel1;
    }

    public double getChargingprice() {
        return chargingprice;
    }

    public void setChargingprice(double chargingprice1) {
        chargingprice = chargingprice1;
    }

    public double getPromoamt() {
        return promoamt;
    }

    public void setPromoamt(double promoamt1) {
        promoamt = promoamt1;
    }

    public double getPoints() {
        return points;
    }

    public void setPoints(double points1) {
        points = points1;
    }

    public int getUserid() {
        return userid;
    }

    public void setUserid(int userid1) {
        userid = userid1;
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid1) {
        pid = pid1;
    }

    public int getUserstatus() {
        return userstatus;
    }

    public void setUserstatus(int userstatus1) {
        userstatus = userstatus1;
    }

    public String getIpaytype() {
        return ipaytype;
    }

    public void setIpaytype(String ipaytype1) {
        ipaytype = ipaytype1;
    }

    public String getPromname() {
        return promname;
    }

    public void setPromname(String promname1) {
        promname = promname1;
    }

    public String getExpiredate() {
        return expiredate;
    }

    public void setExpiredate(String expiredate1) {
        expiredate = expiredate1;
    }

    public String getMtd() {
        return mtd;
    }

    public void setMtd(String mtd1) {
        mtd = mtd1;
    }


}
