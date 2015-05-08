package gov.miamidade.cirm.other;

import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;

import gov.miamidade.cirm.MDJson;
import mjson.Json;

/**
 * Utilities to prepare messages for live reporting.
 * Uses the OWL infrastructure and the reasoner.
 * 
 * Class is fully thread safe (non blocking).
 * 
 * @author Thomas Hilpold
 */
public class LiveReportingUtils
{


	/**
	 * Prepares the message data to be sent to live reporting by augmenting the Json with additional meta data,
	 * not yet added by the OpenCirm Method.
	 * The only modification to srWithMeta is that all iris are being resolved and a property "liveReportingMeta" will be added.
	 * 
	 * This is important as a modification of srWithMeta might affect interface processing.
	 * 
	 * All this information will NOT be added to the case property, which might be expected as is later during processing.
	 * Instead, we add a property liveReportingMeta, where the inforamation will be added.
	 * 
	 * <br/>In particular:
	 * <br/>liveReportingMeta/type will be an object with an iri and a label property
	 * <br/>liveReportingMeta/type/hasJurisdictionCode will be String
	 * <br/>liveReportingMeta/providedBy will be an object with an iri and a label property
	 * <br/>liveReportingMeta/providedBy/parent will be an object with an iri and a label property
	 * <br/>
	 * <br/>Expects to find case/type
	 * <br/>Expects to find case/
	 * 
	 * @param newSRorUpdateSRdata with case/type
	 * @return the same as input with resolved Iris and one additional property "liveReportingMeta".
	 */
	public static void prepareMessageData(Json srWithMeta) {
		OWL.resolveIris(srWithMeta, null);
		Json liveReportingMeta = Json.object();
		MDJson mdj = new MDJson(srWithMeta);
		//Type
		prepareTypeAsObject(mdj, liveReportingMeta);
		prepareProvidedByAsObject(mdj, liveReportingMeta);
		srWithMeta.set("liveReportingMeta", liveReportingMeta);
	}
	
	/**
	 * Finds and prepares the providedBy object of the type for live reporting.
	 * <br/>
	 * <br/> liveReportingMeta/providedBy/iri
	 * <br/> liveReportingMeta/providedBy/label
	 * <br/> liveReportingMeta/providedBy/parent/iri
	 * <br/> liveReportingMeta/providedBy/parent/label
	 * @param mdj
	 * @param liveReportingMeta
	 */
	private static void prepareProvidedByAsObject(MDJson mdj, Json liveReportingMeta)
	{
		Json providedByObj = Json.object();
		OWLNamedIndividual typeInd = getType(mdj);
		OWLNamedIndividual providerInd = OWL.objectProperty(typeInd, "legacy:providedBy");
		if (providerInd != null) {
			String label = OWL.getEntityLabel(providerInd);			
			providedByObj.set("iri", providerInd.getIRI().toString());
			providedByObj.set("label", label);
			OWLNamedIndividual parentInd = OWL.objectProperty(providerInd, "mdc:hasParentAgency");
			if (parentInd != null) {
				Json parentObj = Json.object();
				String parentLabel = OWL.getEntityLabel(parentInd);			
				parentObj.set("iri", parentInd.getIRI().toString());
				parentObj.set("label", parentLabel);
				providedByObj.set("parent", parentObj);
			}			
		}
		liveReportingMeta.set("providedBy", providedByObj);		
	}

	/**
	 * Prepares the type json object with iri and label for live reporting.
	 * 
	 * @param mdj
	 * @param liveReportingMeta
	 */
	private static void prepareTypeAsObject(MDJson mdj, Json liveReportingMeta)
	{
		Json type = mdj.readAt("case.type");
		if (!(type.isObject() && type.has("label"))) {
			Json typeObj = Json.object();
			OWLNamedIndividual typeInd = getType(mdj);
			String label = OWL.getEntityLabel(typeInd);
			if (label != null) {
				typeObj.set("iri", typeInd.getIRI().toString());
				typeObj.set("label", label);
				type = typeObj;
			}
			OWLLiteral jurisdictionCodeLit = OWL.dataProperty(typeInd, "legacy:hasJurisdictionCode");
			if (jurisdictionCodeLit != null) {
				type.set("hasJurisdictionCode", jurisdictionCodeLit.getLiteral());
			}			
			liveReportingMeta.set("type", type);
		}		
	}
	
	/**
	 * Returns the type of the Service Request.
	 * @param mdj
	 * @return
	 */
	private static OWLNamedIndividual getType(MDJson mdj) {
		String typeStr = mdj.readAtStr("case.type");
		OWLNamedIndividual typeInd = OWL.individual((typeStr.startsWith("legacy:")? "" : "legacy:") + typeStr);		
		return typeInd;
	}
}
