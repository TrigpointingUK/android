package com.trigpointinguk.android.types;

public class TrigPhoto {
	private String  iconURL;
	private String  photoURL;
	private String  name;
	private String  descr;
	private String  date;
	private String  username;
	private String  subject;
	private Boolean ispublic;
	private Long    logID;
	
	public TrigPhoto(String name, String descr, String photourl, String iconurl, String username, String date) {
		this.name		= name;
		this.descr		= descr;
		this.photoURL	= photourl;
		this.iconURL	= iconurl;
		this.username	= username;
		this.date		= date;
	}
	
	public TrigPhoto() {
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

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public Boolean getIspublic() {
		return ispublic;
	}

	public void setIspublic(Boolean ispublic) {
		this.ispublic = ispublic;
	}

	public Long getLogID() {
		return logID;
	}

	public void setLogID(Long logID) {
		this.logID = logID;
	}
	
	
}
