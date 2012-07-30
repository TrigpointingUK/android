package com.trigpointinguk.android.filter;

import com.trigpointinguk.android.DbHelper;
import com.trigpointinguk.android.R;
import com.trigpointinguk.android.types.Condition;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;



public class Filter {
	public static final String 	FILTERRADIO			= "filterRadio";
	public static final String 	FILTERTYPE			= "filterType";
	private static final int	TYPESPILLAR			= 0;
	private static final int	TYPESPILLARFBM		= 1;
	private static final int	TYPESNOINTERSECTED	= 2;
	private static final int	TYPESALL			= 3;

	
	private final SharedPreferences mPrefs;

	public Filter (Context context) {
		mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
	}
	
	public String filterWhere() {
		StringBuilder sql = new StringBuilder();
		String tok = "WHERE ";
		
		
		// Deal with RADIO
		switch (mPrefs.getInt(FILTERRADIO, R.id.filterAll)) {
		case R.id.filterLogged:
			sql.append(tok).append("(")
			   .append(DbHelper.TRIG_TABLE).append(".").append(DbHelper.TRIG_LOGGED)
			   .append(" <> '").append(Condition.NOTLOGGED.code()).append("' ")
			   .append(" OR ")
			   .append(DbHelper.LOG_TABLE).append(".").append(DbHelper.LOG_ID).append(" IS NOT NULL")
			   .append(")");
			tok=" AND ";
			break;
		case R.id.filterNotLogged:
			sql.append(tok).append("(")
			   .append(DbHelper.TRIG_TABLE).append(".").append(DbHelper.TRIG_LOGGED)
			   .append(" = '").append(Condition.NOTLOGGED.code()).append("' ")
			   .append(" AND ")
			   .append(DbHelper.LOG_TABLE).append(".").append(DbHelper.LOG_ID).append(" IS NULL")
			   .append(")");
			tok=" AND ";
			break;
		case R.id.filterMarked:
			sql.append(tok)
			   .append(DbHelper.MARK_TABLE).append(".").append(DbHelper.MARK_ID).append(" IS NOT NULL");
			tok=" AND ";
			break;
		case R.id.filterUnsynced:
			sql.append(tok)
			   .append(DbHelper.LOG_TABLE).append(".").append(DbHelper.MARK_ID).append(" IS NOT NULL");
			tok=" AND ";
			break;
		}

		
		return sql.toString();
	}
}
