package net.ticherhaz.vending_duitnow.model;

import java.io.Serializable;

public class CartListModel implements Serializable {

    public int id;
    public int fprodid;
    public int temp;
    public String itemnumber;
    public String itemname;
    public String itemsize;
    public String itemqty;
    public String itemprice;
    public String serial_port;
    public String serial_port_com;
    public String rrp_percent;
    public String img;
    public String isVoucher;
    public String prodid;
    public String position;
    public int quantityCheck;
    public int dspStatu;
    public int quantityMinus;


    public CartListModel() {
    }

    public CartListModel(int id, int temp, int fprodid, String itemnumber, String itemname, String itemsize, String itemqty, String itemprice, String serial_port, String serial_port_com, String rrp_percent, String img, String isVoucher, String prodid) {
        this.id = id;
        this.temp = temp;
        this.fprodid = fprodid;
        this.itemnumber = itemnumber;
        this.itemname = itemname;
        this.itemsize = itemsize;
        this.itemqty = itemqty;
        this.itemprice = itemprice;
        this.serial_port = serial_port;
        this.serial_port_com = serial_port_com;
        this.rrp_percent = rrp_percent;
        this.img = img;
        this.isVoucher = isVoucher;
        this.prodid = prodid;
    }

    public CartListModel(int temp, String itemqty, String serial_port, String serial_port_com) {
        this.temp = temp;
        this.itemqty = itemqty;
        this.serial_port = serial_port;
        this.serial_port_com = serial_port_com;
    }

    public CartListModel(int fprodid, int temp, String itemnumber, String itemname, String itemsize, String itemqty, String itemprice, String serial_port, String serial_port_com, String rrp_percent, String img, String isVoucher, String prodid) {
        this.fprodid = fprodid;
        this.temp = temp;
        this.itemnumber = itemnumber;
        this.itemname = itemname;
        this.itemsize = itemsize;
        this.itemqty = itemqty;
        this.itemprice = itemprice;
        this.serial_port = serial_port;
        this.serial_port_com = serial_port_com;
        this.rrp_percent = rrp_percent;
        this.img = img;
        this.isVoucher = isVoucher;
        this.prodid = prodid;


    }

    public int getQuantityMinus() {
        return quantityMinus;
    }

    public void setQuantityMinus(int quantityMinus) {
        this.quantityMinus = quantityMinus;
    }

    public int getTemp() {
        return temp;
    }

    public void setTemp(int temp) {
        this.temp = temp;
    }

    public int getFprodid() {
        return fprodid;
    }

    public void setFprodid(int fprodid) {
        this.fprodid = fprodid;
    }

    public String getProdid() {
        return prodid;
    }

    public void setProdid(String prodid) {
        this.prodid = prodid;
    }

    public String getVoucher() {
        return isVoucher;
    }

    public void setVoucher(String voucher) {
        isVoucher = voucher;
    }

    public String getSerial_port_com() {
        return serial_port_com;
    }

    public void setSerial_port_com(String serial_port_com) {
        this.serial_port_com = serial_port_com;
    }

    public String getRrp_percent() {
        return rrp_percent;
    }

    public void setRrp_percent(String rrp_percent) {
        this.rrp_percent = rrp_percent;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getItemnumber() {
        return itemnumber;
    }

    public void setItemnumber(String itemnumber) {
        this.itemnumber = itemnumber;
    }

    public String getItemname() {
        return itemname;
    }

    public void setItemname(String itemname) {
        this.itemname = itemname;
    }

    public String getItemsize() {
        return itemsize;
    }

    public void setItemsize(String itemsize) {
        this.itemsize = itemsize;
    }

    public String getItemqty() {
        return itemqty;
    }

    public void setItemqty(String itemqty) {
        this.itemqty = itemqty;
    }

    public String getItemprice() {
        return itemprice;
    }

    public void setItemprice(String itemprice) {
        this.itemprice = itemprice;
    }

    public String getSerial_port() {
        return serial_port;
    }

    public void setSerial_port(String serial_port) {
        this.serial_port = serial_port;
    }

    public String getImg() {
        return img;
    }

    public void setImg(String img) {
        this.img = img;
    }

    public String getPosition() {
        return position;
    }

    public void setPostion(String position) {
        this.position = position;
    }

    public int getQuantityCheck() {
        return quantityCheck;
    }

    public void setQuantityCheck(int quantityCheck) {
        this.quantityCheck = quantityCheck;
    }

    public int getDspStatu() {
        return dspStatu;
    }

    public void setDspStatu(int dspStatu) {
        this.dspStatu = dspStatu;
    }

//    @Override
//    public int compareTo(CartListModel o) {
//        return this.getItemnumber().compareTo(o.getItemnumber());
//    }
}