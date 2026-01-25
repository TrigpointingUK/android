package uk.trigpointing.android.filter;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import android.util.Log;

import uk.trigpointing.android.DbHelper;
import uk.trigpointing.android.types.Condition;
import uk.trigpointing.android.types.Trig;



public class Filter {
    public static final String     FILTERRADIO            = "filterRadio";
    public static final String     FILTERRADIOTEXT        = "filterRadioText";
    public static final String     FILTERTYPE            = "filterType";
    private static final int    TYPESPILLAR            = 0;
    private static final int    TYPESPILLARFBM        = 1;
    private static final int    TYPESFBM            = 2;
    private static final int    TYPESPASSIVE        = 3;
    private static final int    TYPESINTERSECTED    = 4;
    private static final int    TYPESNOINTERSECTED    = 5;
    private static final int    TYPESALL            = 6;
    private static final int    TYPESDEFAULT        = 6; // Default to "All Types"

    
    private final SharedPreferences mPrefs;

    public Filter (Context context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }
    
    public boolean isPillars() {
        switch (mPrefs.getInt(FILTERTYPE, TYPESDEFAULT)) {
        case TYPESPILLAR:
        case TYPESPILLARFBM:
        case TYPESNOINTERSECTED:
        case TYPESALL:
            return true;
        default:
            return false;
        }
    }
    public boolean isFBMs() {
        switch (mPrefs.getInt(FILTERTYPE, TYPESDEFAULT)) {
        case TYPESFBM:
        case TYPESPILLARFBM:
        case TYPESNOINTERSECTED:
        case TYPESALL:
            return true;
        default:
            return false;
        }
    }
    public boolean isPassives() {
        switch (mPrefs.getInt(FILTERTYPE, TYPESDEFAULT)) {
        case TYPESPASSIVE:
        case TYPESNOINTERSECTED:
        case TYPESALL:
            return true;
        default:
            return false;
        }
    }
    public boolean isIntersecteds() {
        switch (mPrefs.getInt(FILTERTYPE, TYPESDEFAULT)) {
        case TYPESINTERSECTED:
        case TYPESALL:
            return true;
        default:
            return false;
        }
    }

    
    public String filterWhere(String initialtok) {
        StringBuilder sql = new StringBuilder();
        String tok = " " + initialtok + " ";
        
        Log.i("Filter", "filterWhere: Starting with initialtok: '" + initialtok + "'");
        
        // Deal with RADIO
        int filterRadio = mPrefs.getInt(FILTERRADIO, 0); // 0 = filterAll
        Log.i("Filter", "filterWhere: Filter radio value: " + filterRadio);
        if (filterRadio == 1) { // filterLogged
            sql.append(tok).append("(")
               .append(DbHelper.TRIG_TABLE).append(".").append(DbHelper.TRIG_LOGGED)
               .append(" <> '").append(Condition.TRIGNOTLOGGED.code()).append("' ")
               .append(" OR ")
               .append(DbHelper.LOG_TABLE).append(".").append(DbHelper.LOG_ID).append(" IS NOT NULL")
               .append(")");
            tok=" AND ";
        } else if (filterRadio == 2) { // filterNotLogged
            sql.append(tok).append("(")
               .append(DbHelper.TRIG_TABLE).append(".").append(DbHelper.TRIG_LOGGED)
               .append(" = '").append(Condition.TRIGNOTLOGGED.code()).append("' ")
               .append(" AND ")
               .append(DbHelper.LOG_TABLE).append(".").append(DbHelper.LOG_ID).append(" IS NULL")
               .append(")");
            tok=" AND ";
        } else if (filterRadio == 3) { // filterMarked
            sql.append(tok)
               .append(DbHelper.MARK_TABLE).append(".").append(DbHelper.MARK_ID).append(" IS NOT NULL");
            tok=" AND ";
        } else if (filterRadio == 4) { // filterUnsynced
            sql.append(tok)
               .append(DbHelper.LOG_TABLE).append(".").append(DbHelper.MARK_ID).append(" IS NOT NULL");
            tok=" AND ";
        }


        
        
        // Deal with TYPES
        int filterType = mPrefs.getInt(FILTERTYPE, TYPESDEFAULT);
        Log.i("Filter", "filterWhere: Filter type value: " + filterType);
        switch (filterType) {
        case TYPESPILLAR:
            sql.append(tok)
               .append(DbHelper.TRIG_TABLE).append(".").append(DbHelper.TRIG_TYPE)
               .append(" = '").append(Trig.Physical.PILLAR.code()).append("' ");
            tok=" AND ";
            break;
        case TYPESPILLARFBM:
            sql.append(tok)
               .append(DbHelper.TRIG_TABLE).append(".").append(DbHelper.TRIG_TYPE)
               .append(" IN ('")
               .append(Trig.Physical.PILLAR.code()).append("', '")
               .append(Trig.Physical.FBM.code()).append("' )");
            tok=" AND ";
            break;
        case TYPESFBM:
            sql.append(tok)
               .append(DbHelper.TRIG_TABLE).append(".").append(DbHelper.TRIG_TYPE)
               .append(" = '").append(Trig.Physical.FBM.code()).append("' ");
            tok=" AND ";
            break;
        case TYPESPASSIVE:
            // Include new PASSIVE category code ('PA') or any legacy type that isn't PILLAR/FBM/INTERSECTED
            sql.append(tok)
               .append("(")
               .append(DbHelper.TRIG_TABLE).append(".").append(DbHelper.TRIG_TYPE)
               .append(" = '").append(Trig.Physical.PASSIVE.code()).append("' OR ")
               .append(DbHelper.TRIG_TABLE).append(".").append(DbHelper.TRIG_TYPE)
               .append(" NOT IN ('")
               .append(Trig.Physical.PILLAR.code()).append("', '")
               .append(Trig.Physical.FBM.code()).append("', '")
               .append(Trig.Physical.INTERSECTED.code()).append("', '")
               .append(Trig.Physical.PASSIVE.code()).append("'))");
            tok=" AND ";
            break;
        case TYPESINTERSECTED:
            sql.append(tok)
               .append(DbHelper.TRIG_TABLE).append(".").append(DbHelper.TRIG_TYPE)
               .append(" = '").append(Trig.Physical.INTERSECTED.code()).append("' ");
            tok=" AND ";
            break;
        case TYPESNOINTERSECTED:
            sql.append(tok)
               .append(DbHelper.TRIG_TABLE).append(".").append(DbHelper.TRIG_TYPE)
               .append(" <> '").append(Trig.Physical.INTERSECTED.code()).append("' ");
            tok=" AND ";
            break;
        }
        
        String result = sql.toString();
        Log.i("Filter", "filterWhere: Final SQL where clause: '" + result + "'");
        return result;
    }
}
