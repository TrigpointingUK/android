package com.trigpointinguk.android.types;

import com.trigpointinguk.android.R;


public enum Condition {
	NOTLOGGED		(" ", R.drawable.c_nolog					, "Not Logged"),
	COULDNTFIND		("N", R.drawable.c_possiblymissing			, "Couldn't Find"),
	GOOD			("G", R.drawable.c_good						, "Good"),
	SLIGHTLYDAMAGED	("S", R.drawable.c_slightlydamaged			, "Slightly Damaged"),
	CONVERTED		("C", R.drawable.c_slightlydamaged			, "Converted"),
	DAMAGED			("D", R.drawable.c_damaged					, "Damaged"),
	REMAINS			("R", R.drawable.c_toppled					, "Remains"),
	TOPPLED			("T", R.drawable.c_toppled					, "Toppled"),
	MOVED			("M", R.drawable.c_toppled					, "Moved"),
	POSSIBLYMISSING	("Q", R.drawable.c_possiblymissing			, "Possibly Missing"),
	MISSING			("X", R.drawable.c_definitelymissing		, "Destroyed"),
	VISIBLE			("V", R.drawable.c_unreachablebutvisible	, "Unreachable but Visible"),
	INACCESSIBLE	("P", R.drawable.c_unknown					, "Inaccessible"),
	UNKNOWN 		("U", R.drawable.c_unknown					, "Unknown"),
	;

	private final String   code;
	private final int	   icon;
	private final String   descr;	
	Condition(String code, int icon, String descr) {
		this.code = code;
		this.icon   = icon;
		this.descr  = descr;
	}
	public String code() {
		return code;
	}
	public int icon() {
		return icon;
	}
	public String toString() {
		return descr;
	}
	public static Condition fromCode(String code) {  
		if (code != null) {
			for (Condition c : values()) {  
				if (code.equals(c.code)) {  
					return c;  
				}  
			}  
		}
		//throw new IllegalArgumentException("Invalid condition: " + code); 
		return UNKNOWN;
	}
}