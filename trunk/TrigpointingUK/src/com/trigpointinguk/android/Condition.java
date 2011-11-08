package com.trigpointinguk.android;


public enum Condition {
	UNKNOWN 		("U", R.drawable.c_unknown					, R.string.condition_U),
	GOOD			("G", R.drawable.c_good						, R.string.condition_G),
	SLIGHTLYDAMAGED	("S", R.drawable.c_slightlydamaged			, R.string.condition_S),
	CONVERTED		("C", R.drawable.c_slightlydamaged			, R.string.condition_C),
	DAMAGED			("D", R.drawable.c_damaged					, R.string.condition_D),
	REMAINS			("R", R.drawable.c_toppled					, R.string.condition_R),
	TOPPLED			("T", R.drawable.c_toppled					, R.string.condition_T),
	MOVED			("M", R.drawable.c_toppled					, R.string.condition_M),
	POSSIBLYMISSING	("Q", R.drawable.c_possiblymissing			, R.string.condition_Q),
	MISSING			("X", R.drawable.c_definitelymissing		, R.string.condition_X),
	VISIBLE			("V", R.drawable.c_unreachablebutvisible	, R.string.condition_V),
	INACCESSIBLE	("P", R.drawable.c_unknown					, R.string.condition_P),
	NOTLOGGED		("N", R.drawable.c_nolog					, R.string.condition_N)
	;

	private final String   code;
	private final int	   icon;
	private final int	   descr;	
	Condition(String code, int icon, int descr) {
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
	public int descr() {
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