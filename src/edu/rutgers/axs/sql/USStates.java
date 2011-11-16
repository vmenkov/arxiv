package edu.rutgers.axs.sql;

public enum USStates {
    //@EA(value="Select One", illegal=true) 	XX,
	@EA(value="Unknown", storeNull=true) 
	__, 
	@EA("ALABAMA")
	AL,
	@EA("ALASKA")
	AK,
	@EA("ARIZONA") 
	AZ,
	@EA("ARKANSAS")
	AR,
	@EA("CALIFORNIA") 
	CA,
	@EA("COLORADO") 
	CO,
	@EA("CONNECTICUT")
	CT,
	@EA("DELAWARE")
	DE,
	@EA("DISTRICT OF COLUMBIA")
	DC,
	@EA("FLORIDA")
	FL,
	@EA("GEORGIA")
	GA,
	@EA("HAWAII")
	HI,
	@EA("IDAHO")
	ID,
	@EA("ILLINOIS")
	IL,
	@EA("INDIANA")
	IN,
	@EA("IOWA")
	IA,
	@EA("KANSAS")
	KS,
	@EA("KENTUCKY")
	KY,
	@EA("LOUISIANA")
	LA,
	@EA("MAINE")
	ME,
	@EA("MARYLAND")
	MD,
	@EA("MASSACHUSETTS")
	MA,
	@EA("MICHIGAN")
	MI,
	@EA("MINNESOTA")
	MN,
	@EA("MISSISSIPPI")
	MS,
	@EA("MISSOURI")
	MO,
	@EA("MONTANA")
	MT,
	@EA("NEBRASKA")
	NE,
	@EA("NEVADA")
	NV,
	@EA("NEW HAMPSHIRE")
	NH,
	@EA("NEW JERSEY")
	NJ,
	@EA("NEW MEXICO")
	NM,
	@EA("NEW YORK")
	NY,
	@EA("NORTH CAROLINA")
	NC,
	@EA("NORTH DAKOTA")
	ND,
	@EA("OHIO")
	OH,
	@EA("OKLAHOMA")
	OK,
	@EA("OREGON")
	OR,
	@EA("PENNSYLVANIA")
	PA,
	@EA("RHODE ISLAND")
	RI,
	@EA("SOUTH CAROLINA")
	SC,
	@EA("SOUTH DAKOTA")
	SD,
	@EA("TENNESSEE")
	TN,
	@EA("TEXAS")
	TX,
	@EA("UTAH")
	UT,
	@EA("VERMONT")
	VT,
	@EA("VIRGINIA") 
	VA,
	@EA("WASHINGTON")
	WA,
	@EA("WEST VIRGINIA")
	WV,
	@EA("WISCONSIN")
	WI,
	@EA("WYOMING")
	WY,
	@EA("AMERICA SAMOA")
	AS,
	@EA("GUAM")
	GU,
	@EA("NORTHERN MARIANA ISLANDS")
	MP,
	@EA("PUERTO RICO") 
	PR,
	@EA("VIRGIN ISLANDS")
	VI,
	@EA("Out of the country")
	OO; 
}
