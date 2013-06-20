package com.callmewill.launcher2.entity;

import java.io.Serializable;

import android.graphics.Bitmap;

public class FavoriteItem implements Serializable {
	
	private int id;
	private String title;
	private String intent;
	private int itemType;
	private Bitmap icon;
	
	
	public FavoriteItem() {
		super();
	}
	public FavoriteItem(int id, String title, String intent, int itemType,
			Bitmap icon) {
		super();
		this.id = id;
		this.title = title;
		this.intent = intent;
		this.itemType = itemType;
		this.icon = icon;
	}
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getIntent() {
		return intent;
	}
	public void setIntent(String intent) {
		this.intent = intent;
	}
	public int getItemType() {
		return itemType;
	}
	public void setItemType(int itemType) {
		this.itemType = itemType;
	}
	public Bitmap getIcon() {
		return icon;
	}
	public void setIcon(Bitmap icon) {
		this.icon = icon;
	}
	
	
	
}
