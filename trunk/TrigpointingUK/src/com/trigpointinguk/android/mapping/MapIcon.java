package com.trigpointinguk.android.mapping;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.trigpointinguk.android.R;
import com.trigpointinguk.android.types.Condition;
import com.trigpointinguk.android.types.Trig;

public class MapIcon {

	public enum colourScheme {NONE, BYCONDITION, BYLOGGED};
	public static final int  defaultIcon = R.drawable.mapicon_pillar_green;
	
	private static final String TAG = "MapIcon";
	private Context mCtx;
	
	private enum iconType 		{PILLAR, FBM, PASSIVE, INTERSECTED};
	private enum iconColour 	{GREEN, RED, YELLOW, GREY};
	private enum iconHighlight 	{NORMAL, HIGHLIGHT};
	// array [type],[colour],[highlight]
	private static final int[][][] icons 	=	{
										{
											{ 
												R.drawable.mapicon_pillar_green,
												R.drawable.mapicon_pillar_green_h 
											},
											{ 
												R.drawable.mapicon_pillar_red,
												R.drawable.mapicon_pillar_red_h 
									  	  	},
											{ 
												R.drawable.mapicon_pillar_yellow,
												R.drawable.mapicon_pillar_yellow_h 
									  	  	},
											{ 
												R.drawable.mapicon_pillar_grey,
												R.drawable.mapicon_pillar_grey_h 
									  	  	}
										},
										{
											{ 
												R.drawable.mapicon_fbm_green,
												R.drawable.mapicon_fbm_green_h 
											},
											{ 
												R.drawable.mapicon_fbm_red,
												R.drawable.mapicon_fbm_red_h 
									  	  	},
											{ 
												R.drawable.mapicon_fbm_yellow,
												R.drawable.mapicon_fbm_yellow_h 
									  	  	},
											{ 
												R.drawable.mapicon_fbm_grey,
												R.drawable.mapicon_fbm_grey_h 
									  	  	}
										},
										{
											{ 
												R.drawable.mapicon_passive_green,
												R.drawable.mapicon_passive_green_h 
											},
											{ 
												R.drawable.mapicon_passive_red,
												R.drawable.mapicon_passive_red_h 
									  	  	},
											{ 
												R.drawable.mapicon_passive_yellow,
												R.drawable.mapicon_passive_yellow_h 
									  	  	},
											{ 
												R.drawable.mapicon_passive_grey,
												R.drawable.mapicon_passive_grey_h 
									  	  	}
										},
										{
											{ 
												R.drawable.mapicon_intersected_green,
												R.drawable.mapicon_intersected_green_h 
											},
											{ 
												R.drawable.mapicon_intersected_red,
												R.drawable.mapicon_intersected_red_h 
									  	  	},
											{ 
												R.drawable.mapicon_intersected_yellow,
												R.drawable.mapicon_intersected_yellow_h 
									  	  	},
											{ 
												R.drawable.mapicon_intersected_grey,
												R.drawable.mapicon_intersected_grey_h 
									  	  	}
										}

								};
	

	
	
    public MapIcon(Context context) {
        mCtx = context;
	}
	
	public Drawable getDrawable(colourScheme colourScheme, Trig.Physical type, Condition condition, Condition logged, Condition unsynced, Boolean flagged) {
		int icon = getIcon(colourScheme, type, condition, logged, unsynced, flagged);
		return mCtx.getResources().getDrawable(icon);
	}
	
	public int getIcon(colourScheme colourScheme, Trig.Physical physical, Condition condition, Condition logged, Condition unsynced, Boolean flagged) {
		iconType 		type		= iconType.PILLAR;
		iconColour 		colour		= iconColour.GREEN;
		iconHighlight 	highlight 	= iconHighlight.NORMAL;
		
		// what sort of icon?
		switch (physical) {
		case PILLAR:
			type=iconType.PILLAR;
			break;
		case FBM:
			type=iconType.FBM;
			break;
		case INTERSECTED:
			type=iconType.INTERSECTED;
			break;
		default:
			type=iconType.PASSIVE;
		}

		// highlighted?
		if (flagged) {
			highlight = iconHighlight.HIGHLIGHT;
		} else {
			highlight = iconHighlight.NORMAL;
		}
		
		// which coondition is used for the colour scheme?
		Condition useCondition;
		switch (colourScheme) {
		case BYCONDITION:
			useCondition = condition;
			break;
		case BYLOGGED:
			if (unsynced == null) {
				useCondition = logged;
			} else {
				// highlight unsynced logs in the same way as marked ones
				highlight = iconHighlight.HIGHLIGHT;
				useCondition = unsynced;
			}
			break;
		default:
			// failsafe
			useCondition = Condition.GOOD;
		}
		
		// apply the colour scheme:
		switch (colourScheme) {		
		case NONE:
			colour = iconColour.GREEN;
			break;
		case BYCONDITION:
		case BYLOGGED:
			switch (useCondition) {
			case UNKNOWN: 
			case NOTLOGGED: 
				colour = iconColour.GREY;
				break;
			case GOOD:
			case SLIGHTLYDAMAGED:
			case DAMAGED:
			case TOPPLED:
			case MOVED:
			case REMAINS:
				colour = iconColour.GREEN;
				break;
			case VISIBLE:
				colour = iconColour.YELLOW;
				break;
			case POSSIBLYMISSING:
			case MISSING:
			case INACCESSIBLE:
			case COULDNTFIND:
				colour = iconColour.RED;
				break;
			default:
				colour = iconColour.GREY;
			}	

			break;
		default:
			break;
		}
		try {
			return icons[type.ordinal()][colour.ordinal()][highlight.ordinal()];
		} catch (ArrayIndexOutOfBoundsException e) {
			Log.w(TAG, "No icon for type " + type + " colour " + colour + " highlight " + highlight);
			return defaultIcon;
		}
	}
}
