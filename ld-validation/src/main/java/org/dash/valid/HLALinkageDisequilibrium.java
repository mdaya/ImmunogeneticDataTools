/*

    Copyright (c) 2014-2015 National Marrow Donor Program (NMDP)

    This library is free software; you can redistribute it and/or modify it
    under the terms of the GNU Lesser General Public License as published
    by the Free Software Foundation; either version 3 of the License, or (at
    your option) any later version.

    This library is distributed in the hope that it will be useful, but WITHOUT
    ANY WARRANTY; with out even the implied warranty of MERCHANTABILITY or
    FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public
    License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this library;  if not, write to the Free Software Foundation,
    Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA.

    > http://www.gnu.org/licenses/lgpl.html

*/
package org.dash.valid;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.dash.valid.ars.HLADatabaseVersion;
import org.dash.valid.freq.HLAFrequenciesLoader;
import org.dash.valid.gl.GLStringConstants;
import org.dash.valid.gl.GLStringUtilities;
import org.dash.valid.gl.LinkageDisequilibriumGenotypeList;
import org.dash.valid.gl.haplo.Haplotype;
import org.dash.valid.gl.haplo.HaplotypeComparator;
import org.dash.valid.gl.haplo.HaplotypePair;
import org.dash.valid.gl.haplo.HaplotypePairComparator;
import org.dash.valid.gl.haplo.HaplotypePairSet;
import org.dash.valid.gl.haplo.HaplotypeSet;
import org.dash.valid.gl.haplo.MultiLocusHaplotype;
import org.dash.valid.report.DetectedDisequilibriumElement;
import org.dash.valid.report.DetectedLinkageFindings;
import org.dash.valid.report.LinkageHitDegree;

/*
 * Linkage disequilibrium
 * 
 * Non-random association of alleles at two or more loci that descend from a single,
 * ancestral chromosome
 * 
 * http://en.wikipedia.org/wiki/Linkage_disequilibrium
 * 
 * This class leverages a specific set of linkage disequilibrium associations relevant in the context
 * of HLA (http://en.wikipedia.org/wiki/Human_leukocyte_antigen)  and immunogenetics:
 * 
 * http://en.wikiversity.org/wiki/HLA/Linkage_Disequilibrium/B-C_Blocks
 * http://en.wikiversity.org/wiki/HLA/Linkage_Disequilibrium/DR-DQ_Blocks
 * 
 */

public class HLALinkageDisequilibrium {
	private static HLADatabaseVersion hladb;
	private static Integer LINKED_HAPLOTYPES_THRESHOLD = new Integer(360);
	
    private static final Logger LOGGER = Logger.getLogger(HLALinkageDisequilibrium.class.getName());
		
	static {
		hladb = HLADatabaseVersion.lookup(System.getProperty(HLADatabaseVersion.HLADB_PROPERTY));
		String hapThreshold;
		if ((hapThreshold = System.getProperty("org.dash.hapThreshold")) != null) {
			LINKED_HAPLOTYPES_THRESHOLD = new Integer(hapThreshold);
		}
	}
			
	public static DetectedLinkageFindings hasLinkageDisequilibrium(LinkageDisequilibriumGenotypeList glString) {		
		Set<HaplotypePair> linkedPairs = new HaplotypePairSet(new HaplotypePairComparator());
		
		Set<String> notCommon = GLStringUtilities.checkCommonWellDocumented(glString.getGLString());
				
		DetectedLinkageFindings findings = new DetectedLinkageFindings();

		Set<Linkages> linkages = LinkagesLoader.getInstance().getLinkages();
		if (linkages == null) {
			return findings;
		}
						
		for (Linkages linkage : linkages) {
			EnumSet<Locus> loci = linkage.getLoci();
			findings.addFindingSought(loci);
			List<DisequilibriumElement> disequilibriumElements = HLAFrequenciesLoader.getInstance().getDisequilibriumElements(loci);
			
			//linkedPairs.addAll(findLinkedPairsFast(glString, loci, disequilibriumElements));	
			linkedPairs.addAll(findLinkedPairs(glString, loci, disequilibriumElements));
		}		
		
		LOGGER.info(linkedPairs.size() + " linkedPairs");
		
		findings.setGenotypeList(glString);
		findings.setLinkedPairs(linkedPairs);
		findings.setNonCWDAlleles(notCommon);
		findings.setHladb(hladb);
		
		return findings;
	}
	
	private static Set<HaplotypePair> findLinkedPairsFast(
			LinkageDisequilibriumGenotypeList glString,
			EnumSet<Locus> loci,
			List<DisequilibriumElement> disequilibriumElements) {
		Set<HaplotypePair> linkedPairs = new HaplotypePairSet(new HaplotypePairComparator());

		Set<MultiLocusHaplotype> linkedHaplotypes = new HashSet<MultiLocusHaplotype>();
		
		for (MultiLocusHaplotype possibleHaplotype : glString.getPossibleHaplotypes(loci)) {
			HashMap<Locus, String> hlaElementMap = new HashMap<Locus, String>();

			for (Locus locus : possibleHaplotype.getLoci()) {
				if (loci.contains(locus)) {
					hlaElementMap.put(locus, possibleHaplotype.getAlleles(locus).get(0));
				}
			}
			
			DisequilibriumElement element = new CoreDisequilibriumElement(hlaElementMap, possibleHaplotype);
			
			if (disequilibriumElements.contains(element)) {
				int index = disequilibriumElements.indexOf(element);
				possibleHaplotype.setLinkage(new DetectedDisequilibriumElement(disequilibriumElements.get(index)));
				linkedHaplotypes.add(possibleHaplotype);
			}
		}
		
		for (Haplotype haplotype1 : linkedHaplotypes) {	
			for (Haplotype haplotype2 : linkedHaplotypes) {
				int idx = 0;
				for (Locus locus : loci) {
					if ((!glString.hasHomozygous(locus) && haplotype1.getHaplotypeInstance(locus) == haplotype2.getHaplotypeInstance(locus))) {
						// move on to next haplotype2
						break;
					}
					
					if (idx == loci.size() - 1) {
						linkedPairs.add(new HaplotypePair(haplotype1, haplotype2));
					}
					
					idx++;
				}
			}
		}
		
		return linkedPairs;
	}

	private static Set<HaplotypePair> findLinkedPairs(
			LinkageDisequilibriumGenotypeList glString,
			EnumSet<Locus> loci,
			List<DisequilibriumElement> disequilibriumElements) {
		List<Haplotype> linkedHaplotypes = findLinkedHaplotypes(disequilibriumElements, glString, loci);
		
		// create the pairs
		
		Set<HaplotypePair> linkedPairs = new HashSet<HaplotypePair>();
		
		LOGGER.info(linkedHaplotypes.size() + " linked " + loci + " haplotypes");
		if (linkedHaplotypes.size() > LINKED_HAPLOTYPES_THRESHOLD) {
			LOGGER.warning("Linked " + loci + " haplotype count: " + linkedHaplotypes.size() + " exceeds configured threshold: " + LINKED_HAPLOTYPES_THRESHOLD + ".  Not calculating relative frequencies.");
		}
		else {	
			for (Haplotype haplotype1 : linkedHaplotypes) {	
				for (Haplotype haplotype2 : linkedHaplotypes) {
					int idx = 0;
					for (Locus locus : loci) {
						if ((!glString.hasHomozygous(locus) && haplotype1.getHaplotypeInstance(locus) == haplotype2.getHaplotypeInstance(locus))) {
							// move on to next haplotype2
							break;
						}
						
						if (idx == loci.size() - 1) {
							linkedPairs.add(new HaplotypePair(haplotype1, haplotype2));
						}
						
						idx++;
					}
				}
			}
		}
		
		return linkedPairs;
	}

	private static List<Haplotype> findLinkedHaplotypes(List<DisequilibriumElement> disequilibriumElements, LinkageDisequilibriumGenotypeList glString,
			EnumSet<Locus> loci) {
		List<Haplotype> linkedHaplotypes = new ArrayList<Haplotype>();
		for (DisequilibriumElement disElement : disequilibriumElements) {
			linkedHaplotypes.addAll(detectLinkages(glString, disElement, loci));
		}
		
		return linkedHaplotypes;
	}
	
	public static Set<Haplotype> detectLinkages(LinkageDisequilibriumGenotypeList glString, DisequilibriumElement disElement, EnumSet<Locus> loci) {
		Set<MultiLocusHaplotype> possibleHaplotypes = glString.getPossibleHaplotypes(loci);
		
		Set<Haplotype> linkedHaplotypes = new HaplotypeSet(new HaplotypeComparator());
		
		for (MultiLocusHaplotype haplotype : possibleHaplotypes) {
			linkedHaplotypes = detectLinkages(glString, linkedHaplotypes, disElement, haplotype);
		}
		
		return linkedHaplotypes;
	}
	
	private static DetectedDisequilibriumElement buildFoundElement(DetectedDisequilibriumElement foundElement, DisequilibriumElement disElement, 
			Locus locus, LinkageHitDegree hitDegree) {
		if (foundElement == null) {
			foundElement = new DetectedDisequilibriumElement(disElement);
		}
		
		foundElement.setHitDegree(locus, hitDegree);
		
		return foundElement;
	}
	
	private static Set<Haplotype> detectLinkages(LinkageDisequilibriumGenotypeList glString, Set<Haplotype> linkedHaplotypes, DisequilibriumElement disElement, MultiLocusHaplotype haplotype) {
		LinkageHitDegree hitDegree;
		DetectedDisequilibriumElement foundElement = null;
		boolean noHits = true;

		for (Locus locus : haplotype.getLoci()) {			
			for (String allele : haplotype.getAlleles(locus)) {
				hitDegree = GLStringUtilities.fieldLevelComparison(allele, disElement.getHlaElement(locus));
				
				if (Locus.HLA_DRB345.equals(locus) && glString.hasHomozygous(Locus.HLA_DRB345) && (disElement.getHlaElement(Locus.HLA_DRB345).equals(GLStringConstants.DASH) || disElement.getHlaElement(Locus.HLA_DRB345).equals(GLStringConstants.NNNN))) {
					hitDegree = new LinkageHitDegree(GLStringUtilities.P_GROUP_LEVEL, GLStringUtilities.P_GROUP_LEVEL, GLStringConstants.NNNN, GLStringConstants.NNNN);
					foundElement = buildFoundElement(foundElement, disElement, Locus.HLA_DRB345, hitDegree);
					noHits = false;
					if (foundElement.getLoci().containsAll(haplotype.getLoci())) {
						linkedHaplotypes.add(new MultiLocusHaplotype(foundElement, haplotype));
						foundElement = null;
					}
					break;
				} 
				else if (hitDegree != null || (hitDegree = GLStringUtilities.checkAntigenRecognitionSite(allele, disElement.getHlaElement(locus))) != null) {
					foundElement = buildFoundElement(foundElement, disElement,locus, hitDegree);
					noHits = false;
					if (foundElement.getLoci().containsAll(haplotype.getLoci())) {
						linkedHaplotypes.add(new MultiLocusHaplotype(foundElement, haplotype));
						foundElement = null;
					}
					break;
				}
			}
			if (noHits) {
				break;
			}
		}
		
		return linkedHaplotypes;
	}
}
