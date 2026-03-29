package com.openrsc.server.external;

/**
 * The definition wrapper for items.
 * Note: Integer is used for some properties because it supports null (= not present in XML).
 */
public class ItemCookingDef {
	/**
	 * The exp cooking this item gives
	 */
	public int exp;

  /**
	 * The id of the cooked version
	 */
	public int cookedId;

	/**
	 * The id of the burned version
	 */
	public int burnedId;

	/**
	 * The level required to cook this
	 */
	public int requiredLvl;

	/**
	 * The low value. Used to calculate burn chance on normal ranges.
	 */
	public Integer low;

	/**
	 * The high value. Used to calculate burn chance on normal ranges.
	 */
	public Integer high;

	/**
	 * The low fire value. Used to calculate burn chance on campfires and fireplaces.
	 */
	public Integer lowFire;

	/**
	 * The high fire value. Used to calculate burn chance on campfires and fireplaces.
	 */
	public Integer highFire;

	/**
	 * The low cook's range value. Used to calculate burn chance on cook's ranges (e.g. Lumbridge range).
	 * If it has no value, it means the burn rate is not any different on a cook's range.
	 */
	public Integer lowCooksRange;

	/**
	 * The high cook's range value. Used to calculate burn chance on cook's ranges (e.g. Lumbridge range).
	 * If it has no value, it means the burn rate is not any different on a cook's range.
	 */
	public Integer highCooksRange;

	/**
	 * The low gauntlets value. Used to calculate burn chance when gauntlets are equipped.
	 */
	public Integer lowGauntlets;

	/**
	 * The high gauntlets value. Used to calculate burn chance when gauntlets are equipped.
	 */
	public Integer highGauntlets;

  public int getExp() {
		return exp;
	}

  public int getCookedId() {
		return cookedId;
	}

	public int getBurnedId() {
		return burnedId;
	}

	public int getReqLevel() {
		return requiredLvl;
	}

	public Integer getLow() {
		return low;
	}

	public Integer getHigh() {
		return high;
	}

	public Integer getLowFire() {
		return lowFire;
	}

	public Integer getHighFire() {
		return highFire;
	}

	public Integer getLowCooksRange() {
		return lowCooksRange;
	}

	public Integer getHighCooksRange() {
		return highCooksRange;
	}

	public Integer getLowGauntlets() {
		return lowGauntlets;
	}

	public Integer getHighGauntlets() {
		return highGauntlets;
	}
}
