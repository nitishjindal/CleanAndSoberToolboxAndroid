package com.osu.cleanandsobertoolboxandroid;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.osu.cleanandsobertoolboxandroid.MessageDbContract.*;

public class MessageDataSource {
	private SQLiteDatabase database;
	private MessageReaderDbHelper mDbHelper;
	private Context context;
	
	public MessageDataSource(Context context) {
		this.context = context;
		mDbHelper = new MessageReaderDbHelper(context);
	}
	
	public void open() throws SQLException {
	    database = mDbHelper.getWritableDatabase();
	}

	public void close() {
		mDbHelper.close();
	}
	
	// Read the JSON file and return its string representation
	public String ProcessJSONFile(int fileId) {
		InputStream is = null;
		String result = null;
	    try {
	    	is = context.getResources().openRawResource(fileId);
	        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 8);
	        StringBuilder sb = new StringBuilder();

	        String line = null;
	        while ((line = reader.readLine()) != null)
	        {
	            sb.append(line + "\n");
	        }
	        result = sb.toString();
	        reader.close();
	    } catch (Exception e) { 
	    	e.printStackTrace();
	    }
	    finally {
	        try{if(is != null) is.close();} catch(Exception squish){}
	    }
	    
	    return result;
	}
	
	// Populate the database from the JSON objects passed as string
	public void PopulateDb(String categories, String messages) {
		try {
			JSONArray jMessages = new JSONArray(messages);
			JSONObject jCategories = new JSONObject(categories);
			
			// This will execute all the insert statements in a single transaction 
		    // rather than creating a new transaction for every insert statement
		    database.beginTransaction();
		    try {
			    InsertMessages(jMessages);
			    InsertCategories(jCategories.getJSONArray("subcategories"), -1);
			    database.setTransactionSuccessful();
		    } finally {
		    	database.endTransaction();
		    }
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	    
	public void InsertMessages(JSONArray messages) {
		try {    
		    for (int i = 0; i < messages.length(); i++) {
		    	ContentValues values = new ContentValues();
		    	JSONObject message = messages.getJSONObject(i);
		    	values.put(MessageEntry.COLUMN_NAME_ENTRY_ID, 
		    			message.getInt("id"));
		    	values.put(MessageEntry.COLUMN_NAME_TITLE,
		    			message.getString("title"));
		    	values.put(MessageEntry.COLUMN_NAME_MESSAGE,
		    			message.getString("content"));
		    	values.put(MessageEntry.COLUMN_NAME_TODO,
		    			message.getString("todo"));
		    	
		    	// Insert the new row, returning the primary key value of the new row
		    	database.insert(
		    			MessageEntry.TABLE_NAME,
		    			null,
		    			values);
		    }
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	public void InsertCategories(JSONArray categories, int parent) {
		try {
			for(int i = 0; i < categories.length(); i++)
		    {
		    	ContentValues values = new ContentValues();
		    	JSONObject category = categories.getJSONObject(i);
		    	
		    	int id = category.getInt("id");
		    	values.put(StructureEntry.COLUMN_NAME_ENTRY_ID,
		    			id);
		    	values.put(StructureEntry.COLUMN_NAME_PARENT_ID,
		    			parent);
		    	values.put(StructureEntry.COLUMN_NAME_TYPE,
		    			category.getString("type"));
		    	values.put(StructureEntry.COLUMN_NAME_TITLE,
		    			category.getString("title"));
		    	
		    	database.insert(StructureEntry.TABLE_NAME, null, values);
		    	
		    	JSONArray messages = category.getJSONArray("messages");
		    	if (messages.length() > 0) {
		    		for (int j = 0; j < messages.length(); ++j) {
		    			values.clear();
		    			
		    			values.put(StructureEntry.COLUMN_NAME_ENTRY_ID,
		    					messages.getInt(j));
		    			values.put(StructureEntry.COLUMN_NAME_PARENT_ID,
		    					id);
		    			values.put(StructureEntry.COLUMN_NAME_TYPE,
		    					"content");
		    			values.putNull(StructureEntry.COLUMN_NAME_TITLE);
		    			
		    			database.insert(StructureEntry.TABLE_NAME, null, values);
		    		}
		    	}
		    	
		    	JSONArray subcategories = category.getJSONArray("subcategories");
		    	// Recursively call InstertStructureEntries to insert the sub-categories
		    	// within the current category
		    	if(subcategories.length() > 0) {
		    		InsertCategories(subcategories, id);
		    	}
		    }
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	// Empty the entire database
	public void EmptyDb() {
		String deleteStructure = "DELETE FROM " + StructureEntry.TABLE_NAME;
		String deleteMessages = "DELETE FROM " + MessageEntry.TABLE_NAME;
		
		database.execSQL(deleteStructure);
		database.execSQL(deleteMessages);
	}
	
	public static boolean databaseExist(Context context, String dbName) {
	    File dbFile=context.getDatabasePath(dbName);
	    return dbFile.exists();
	}
	
	public List<Category> getAllChildren(Context context, int parentId) {
		List<Category> categories = new ArrayList<Category>();
		
		String[] projection = {
				StructureEntry.COLUMN_NAME_ENTRY_ID,
				StructureEntry.COLUMN_NAME_TITLE,
				StructureEntry.COLUMN_NAME_TYPE
		};
		
		String selection = StructureEntry.COLUMN_NAME_PARENT_ID + " = " + parentId;
		
		Cursor c = database.query( StructureEntry.TABLE_NAME, projection,
				selection, null, null, null, null );
		
		if (c.moveToFirst()) {
			do {
				String type = c.getString(2);
				if (type.equals("category")) {
					categories.add(new Category(c.getInt(0), c.getString(1), type));
				} else if (type.equals("content")) {
					// Get the content title from "messages" table
					String[] mprojection = {
							MessageEntry.COLUMN_NAME_TITLE
					};
					String mselection = MessageEntry.COLUMN_NAME_ENTRY_ID + " = " + c.getInt(0);
					
					Cursor m_c = database.query( MessageEntry.TABLE_NAME, mprojection,
							mselection, null, null, null, null );
					
					if (m_c.moveToFirst()) {
						categories.add(new Category(c.getInt(0), m_c.getString(0), type));
					}
				}
			} while(c.moveToNext());
		}
		
		return categories;
	}
	
	public String getMessage(Context context, int messageId) {
		String message = "";
		
		String[] mprojection = {
				MessageEntry.COLUMN_NAME_TITLE,
				MessageEntry.COLUMN_NAME_MESSAGE,
				MessageEntry.COLUMN_NAME_TODO
		};
		String mselection = MessageEntry.COLUMN_NAME_ENTRY_ID + " = " + messageId;
		
		Cursor c = database.query( MessageEntry.TABLE_NAME, mprojection,
				mselection, null, null, null, null );
		
		if (c.moveToFirst()) {
			message = c.getString(1) + "<br><br>" + c.getString(2);
		}
		
		return message;
	}
	

	public int getRandomIndex(){
		int index = 0;

		String rand = "RANDOM";
		String one = "1";
		String[] mprojection = {
				MessageEntry.COLUMN_NAME_ENTRY_ID
		};
		
		//Query db for random message index
		Cursor mCur = database.rawQuery("SELECT identifier FROM messages ORDER BY RANDOM() LIMIT 1", null);
		if (mCur != null)
		{
			mCur.moveToFirst();
		}
		
		//Should only be one column with one row
		index=mCur.getInt(0);
		
		
		return index;
	}
	

	public List<Category> getQueryMatches(String query) {
		String[] mprojection = {
				MessageEntry.COLUMN_NAME_ENTRY_ID,
				MessageEntry.COLUMN_NAME_TITLE,
				MessageEntry.COLUMN_NAME_MESSAGE,
				MessageEntry.COLUMN_NAME_TODO
		};
	    String mselection = MessageEntry.TABLE_NAME + " MATCH '" + query + "'";
	    
	    Cursor mcursor = database.query( MessageEntry.TABLE_NAME, mprojection, 
	    		mselection, null, null, null, null );
	    
	    List<Category> categories = new ArrayList<Category>();
	    if (mcursor.moveToFirst()) {
	    	do {
	    		categories.add(new Category(mcursor.getInt(0), mcursor.getString(1), "content"));
	    	} while (mcursor.moveToNext());

	    }
	    /*
	    String[] projection = {
				StructureEntry.COLUMN_NAME_ENTRY_ID,
				StructureEntry.COLUMN_NAME_TITLE,
				StructureEntry.COLUMN_NAME_TYPE
		};
		
		String selection = StructureEntry.COLUMN_NAME_TITLE + " MATCH '" + query + "'";
		
		Cursor scursor = database.query( StructureEntry.TABLE_NAME, projection, 
	    		selection, null, null, null, null );
		if (scursor.moveToFirst()) {
	    	do {
	    		categories.add(new Category(scursor.getInt(0), scursor.getString(1), scursor.getString(2)));
	    	} while (scursor.moveToNext());
	    }
	    */
	    return categories;
	}
}
