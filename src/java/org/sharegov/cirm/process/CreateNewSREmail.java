package org.sharegov.cirm.process;

import static org.sharegov.cirm.OWL.dataProperty;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;

import java.util.ArrayList;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.BOUtils;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.legacy.CirmMimeMessage;
import org.sharegov.cirm.legacy.MessageManager;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

public class CreateNewSREmail implements ApprovalSideEffect
{

	@Override
	public void execute(ApprovalProcess approvalProcess)
	{
		String type = approvalProcess.getSr().at("type").asString();
		BOntology bontology = approvalProcess.getBOntology();
		OWLNamedIndividual emailTemplate = objectProperty(individual(type), "legacy:hasEmailTemplate");
		
		ArrayList<BOntology> withMetadata = new ArrayList<BOntology>();					
		if (emailTemplate != null)
		{
			try
			{
				BOntology withMeta = BOUtils.addMetaDataAxioms(bontology);
				withMetadata.add(withMeta);
				approvalProcess.setWithMetadata(withMetadata);
				CirmMimeMessage msg = MessageManager.get().createMimeMessageFromTemplate(
						withMeta,
						dataProperty(individual(type),
								"legacy:hasLegacyCode"), emailTemplate);
				msg.addExplanation("createNewKOSR SR template " + emailTemplate.getIRI().getFragment());
				approvalProcess.getMsgsToSend().add(msg);
			}
			catch (Throwable t)
			{
				ThreadLocalStopwatch.error("Error createNewKOSR - Failed to create email for " + bontology.getObjectId());
			}
		}
		
	}

}
