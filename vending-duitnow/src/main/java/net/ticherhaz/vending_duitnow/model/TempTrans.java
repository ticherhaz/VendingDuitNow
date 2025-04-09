package net.ticherhaz.vending_duitnow.model;

import java.util.Date;

public class TempTrans {

    public int TransID;
    public int UserID;
    public Date TransDate;
    public String FranID;
    public String MachineID;
    public String ProductIDs;
    public double Amount;
    public String PaymentType;
    public String PaymentMethod;
    public int PaymentStatus;
    public String FreePoints;
    public String Promocode;
    public String PromoAmt;
    public String Vouchers;
    public String PaymentStatusDes;

    public TempTrans() {
    }

    public String getPaymentStatusDes() {
        return PaymentStatusDes;
    }

    public void setPaymentStatusDes(String paymentStatusDes) {
        PaymentStatusDes = paymentStatusDes;
    }

    public int getTransID() {
        return TransID;
    }

    public void setTransID(int transID) {
        TransID = transID;
    }

    public int getUserID() {
        return UserID;
    }

    public void setUserID(int userID) {
        UserID = userID;
    }

    public Date getTransDate() {
        return TransDate;
    }

    public void setTransDate(Date transDate) {
        TransDate = transDate;
    }

    public String getFranID() {
        return FranID;
    }

    public void setFranID(String franID) {
        FranID = franID;
    }

    public String getMachineID() {
        return MachineID;
    }

    public void setMachineID(String machineID) {
        MachineID = machineID;
    }

    public String getProductIDs() {
        return ProductIDs;
    }

    public void setProductIDs(String productIDs) {
        ProductIDs = productIDs;
    }

    public double getAmount() {
        return Amount;
    }

    public void setAmount(double amount) {
        Amount = amount;
    }

    public String getPaymentType() {
        return PaymentType;
    }

    public void setPaymentType(String paymentType) {
        PaymentType = paymentType;
    }

    public String getPaymentMethod() {
        return PaymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        PaymentMethod = paymentMethod;
    }

    public int getPaymentStatus() {
        return PaymentStatus;
    }

    public void setPaymentStatus(int paymentStatus) {
        PaymentStatus = paymentStatus;
    }

    public String getFreePoints() {
        return FreePoints;
    }

    public void setFreePoints(String freePoints) {
        FreePoints = freePoints;
    }

    public String getPromocode() {
        return Promocode;
    }

    public void setPromocode(String promocode) {
        Promocode = promocode;
    }

    public String getPromoAmt() {
        return PromoAmt;
    }

    public void setPromoAmt(String promoAmt) {
        PromoAmt = promoAmt;
    }

    public String getVouchers() {
        return Vouchers;
    }

    public void setVouchers(String vouchers) {
        Vouchers = vouchers;
    }
}
