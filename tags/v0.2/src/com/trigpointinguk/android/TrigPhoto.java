package com.trigpointinguk.android;

public class TrigPhoto {
	private String iconURL;
	private String photoURL;
	private String name;
	private String descr;
	private String date;
	private String username;
	
	public TrigPhoto(String name, String descr, String photourl, String iconurl, String username, String date) {
		this.name		= name;
		this.descr		= descr;
		this.photoURL	= photourl;
		this.iconURL	= iconurl;
		this.username	= username;
		this.date		= date;
	}
	
	public String getIconURL() {
		return iconURL;
	}
	public void setIconURL(String iconURL) {
		this.iconURL = iconURL;
	}
	public String getPhotoURL() {
		return photoURL;
	}
	public void setPhotoURL(String photoURL) {
		this.photoURL = photoURL;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDescr() {
		return descr;
	}
	public void setDescr(String descr) {
		this.descr = descr;
	}
	public String getDate() {
		return date;
	}
	public void setDate(String date) {
		this.date = date;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	
	
}
