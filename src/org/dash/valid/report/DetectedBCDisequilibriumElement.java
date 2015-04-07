package org.dash.valid.report;

import org.dash.valid.BCDisequilibriumElement;

public class DetectedBCDisequilibriumElement extends DetectedDisequilibriumElement {
	private LinkageHitDegree bHitDegree;
	private LinkageHitDegree cHitDegree;
	
	public DetectedBCDisequilibriumElement(BCDisequilibriumElement disequilibriumElement) {
		setDisequilibriumElement(disequilibriumElement);
	}
	
	@Override
	public BCDisequilibriumElement getDisequilibriumElement() {
		return (BCDisequilibriumElement) super.getDisequilibriumElement();
	}
	
	public LinkageHitDegree getbHitDegree() {
		return bHitDegree;
	}

	public void setbHitDegree(LinkageHitDegree bHitDegree) {
		this.bHitDegree = bHitDegree;
	}

	public LinkageHitDegree getcHitDegree() {
		return cHitDegree;
	}

	public void setcHitDegree(LinkageHitDegree cHitDegree) {
		this.cHitDegree = cHitDegree;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer("B Locus: " + getDisequilibriumElement().getHlabElement() + " (" + getbHitDegree() + ")\n" +
				"C Locus: " + getDisequilibriumElement().getHlacElement() + " (" + getcHitDegree() + ")\n" + 
				getDisequilibriumElement().getFrequencyInfo());
		
		return sb.toString();
	}
}
