package org.sharegov.cirm.model;

import mjson.Json;

/**
 * Immutable Address representation for Cirm.
 *
 * @author Thomas Hilpold
 *
 */
public class CirmAddress {
	
	private boolean isLocation;
	private String addressType;
	private String fullAddress;
	
	private CirmLabeledIndividual street_Address_City; 
	private CirmLabeledIndividual street_Address_State; 
	private Long zip_Code;
	private String street_Unit_Number;
	private String hasLocationName;
	private Long street_Number;
	private String street_Name;
	private CirmLabeledIndividual street_Direction;
	private CirmLabeledIndividual hasStreetType;
	
	public static final CirmAddress createFrom(Json address) {
		CirmAddress a = new CirmAddress();
		a.addressType = address.at("addressType").isNull() ? "" : 
			address.at("addressType").asString();
		a.fullAddress = address.at("fullAddress").isNull() ? "" : 
			address.at("fullAddress").asString();
		a.street_Address_City = CirmLabeledIndividual.createFrom(address.at("Street_Address_City"));
		a.street_Address_State = CirmLabeledIndividual.createFrom(address.at("Street_Address_State"));
		a.zip_Code = address.at("Zip_Code").isNull() ? 0 : 
			address.at("Zip_Code").asLong();
		a.street_Unit_Number = address.has("Street_Unit_Number") ? address.at(
			"Street_Unit_Number").asString() : null;
		a.hasLocationName = address.has("hasLocationName") ? address.at(
			"hasLocationName").asString() : null;

		if (address.has("Street_Number") && !address.at("Street_Number").asString().isEmpty()) {
			//Street num comes as long parsable string or empty string.
			a.street_Number = address.at("Street_Number").asLong(); 
		} else {
			a.street_Number = null;
		}
		a.street_Name = address.has("Street_Name") && address.at("Street_Name").isString()? address.at("Street_Name").asString() : null;
		a.street_Direction = CirmLabeledIndividual.createFrom(address.at("Street_Direction")); //streetPrfx 
		a.hasStreetType = CirmLabeledIndividual.createFrom(address.at("hasStreetType")); //streetSufx
		//isLocation? false => intersection or corr or area
		a.isLocation = a.street_Number != null && a.street_Name != null && a.hasStreetType != null;
		return a;
	}

	private CirmAddress() {
	}
	
	/**
	 * true if street_Number, street_Name and hasStreetType are not null.
	 * False if intersection, corr or area.
	 * @return
	 */
	public final boolean isLocation() {
		return isLocation;
	}

	public final String getAddressType() {
		return addressType;
	}

	public final String getFullAddress() {
		return fullAddress;
	}

	public final CirmLabeledIndividual getStreet_Address_City() {
		return street_Address_City;
	}

	public final CirmLabeledIndividual getStreet_Address_State() {
		return street_Address_State;
	}

	public final Long getZip_Code() {
		return zip_Code;
	}

	public final String getStreet_Unit_Number() {
		return street_Unit_Number;
	}

	public final String getHasLocationName() {
		return hasLocationName;
	}

	public final Long getStreet_Number() {
		return street_Number;
	}

	public final String getStreet_Name() {
		return street_Name;
	}

	public final CirmLabeledIndividual getStreet_Direction() {
		return street_Direction;
	}

	public final CirmLabeledIndividual getHasStreetType() {
		return hasStreetType;
	}

}
