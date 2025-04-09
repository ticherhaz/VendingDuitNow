package net.ticherhaz.vending_duitnow;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


import net.ticherhaz.vending_duitnow.model.CongifModel;

import java.util.ArrayList;
import java.util.List;

public class configdata extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "configManager";
    private static final String TABLE_CONFIGITEMS = "congifgdetails";
    private static final String KEY_ID = "id";
    private static final String KEY_FID = "fid";
    private static final String KEY_MID = "mid";
    private static final String KEY_MCODE = "merchantcode";
    private static final String KEY_MKEY = "merchantkey";
    private static final String KEY_IPPAY = "ipforpaywave";
    private static final String KEY_TOOPID = "toopid";
    private static final String KEY_TOOPPASS = "tooppass";

    public configdata(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        String CREATE_CONFIG_TABLE = "CREATE TABLE " + TABLE_CONFIGITEMS + "("
                + KEY_ID + " INTEGER PRIMARY KEY,"
                + KEY_FID + " TEXT,"
                + KEY_MID + " TEXT,"
                + KEY_MCODE + " TEXT,"
                + KEY_MKEY + " TEXT,"
                + KEY_IPPAY + " TEXT,"
                + KEY_TOOPID + " TEXT,"
                + KEY_TOOPPASS + " TEXT" + ")";
        db.execSQL(CREATE_CONFIG_TABLE);

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONFIGITEMS);

        // Create tables again
        onCreate(db);

    }

    // code to add the new item
    public void addItem(CongifModel congifModel) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_FID, congifModel.getFid());
        values.put(KEY_MID, congifModel.getMid());
        values.put(KEY_MCODE, congifModel.getMerchantcode());
        values.put(KEY_MKEY, congifModel.getMerchantkey());
        values.put(KEY_IPPAY, congifModel.getIpforpaywave());
        values.put(KEY_TOOPID, congifModel.getToopid());
        values.put(KEY_TOOPPASS, congifModel.getTooppass());

        // Inserting Row
        db.insert(TABLE_CONFIGITEMS, null, values);
        //2nd argument is String containing nullColumnHack
        db.close(); // Closing database connection
    }


    // code to get the single item
    public CongifModel getItem(int id) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_CONFIGITEMS, new String[]{KEY_ID,
                        KEY_FID, KEY_MID, KEY_MCODE, KEY_MKEY, KEY_IPPAY, KEY_TOOPID, KEY_TOOPPASS}, KEY_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null, null);
        if (cursor != null)
            cursor.moveToFirst();

        CongifModel congifModel = new CongifModel(
                Integer.parseInt(cursor.getString(0)),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getString(4),
                cursor.getString(5),
                cursor.getString(6),
                cursor.getString(7));

        return congifModel;
    }


    // code to get all items in a list view
    public List<CongifModel> getAllItems() {
        List<CongifModel> congifModelArrayList = new ArrayList<CongifModel>();
        try {
            // Select All Query
            String selectQuery = "SELECT  * FROM " + TABLE_CONFIGITEMS;

            SQLiteDatabase db = this.getWritableDatabase();
            Cursor cursor = db.rawQuery(selectQuery, null);

            // looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                do {
                    CongifModel congifModel = new CongifModel();
                    congifModel.setId(Integer.parseInt(cursor.getString(0)));
                    congifModel.setFid(cursor.getString(1));
                    congifModel.setMid(cursor.getString(2));
                    congifModel.setMerchantcode(cursor.getString(3));
                    congifModel.setMerchantkey(cursor.getString(4));
                    congifModel.setIpforpaywave(cursor.getString(5));
                    congifModel.setToopid(cursor.getString(6));
                    congifModel.setTooppass(cursor.getString(7));


                    // Adding items to list
                    congifModelArrayList.add(congifModel);
                } while (cursor.moveToNext());
            }
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        } catch (Exception ex) {
        }
        // return contact list
        return congifModelArrayList;
    }

    // code to update the single item
    public int updateitem(CongifModel congifModel) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();

        values.put(KEY_FID, congifModel.getFid());
        values.put(KEY_MID, congifModel.getMid());
        values.put(KEY_MCODE, congifModel.getMerchantcode());
        values.put(KEY_MKEY, congifModel.getMerchantkey());
        values.put(KEY_IPPAY, congifModel.getIpforpaywave());
        values.put(KEY_TOOPID, congifModel.getToopid());
        values.put(KEY_TOOPPASS, congifModel.getTooppass());


        // updating row
        return db.update(TABLE_CONFIGITEMS, values, KEY_ID + " = ?",
                new String[]{String.valueOf(congifModel.getId())});
    }


    // Deleting single item
    public void deleteitem(CongifModel congifModel) {
        SQLiteDatabase db = this.getWritableDatabase();

        db.delete(TABLE_CONFIGITEMS, KEY_ID + " = ?",
                new String[]{String.valueOf(congifModel.getId())});
        db.close();
    }

    // Getting item Count
    public int getItemCount() {
        String countQuery = "SELECT  * FROM " + TABLE_CONFIGITEMS;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);


        // return count
        return cursor.getCount();
    }

    // Deleting all item
    public void deleteallitems() {
        SQLiteDatabase db = this.getWritableDatabase();
        //Delete all records of table
        db.execSQL("DELETE FROM " + TABLE_CONFIGITEMS);

        //For go back free space by shrinking sqlite file
        db.execSQL("VACUUM");
        db.close();
    }


}