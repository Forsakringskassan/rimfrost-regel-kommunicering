package se.fk.github.kommunicering.presentation.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;
import se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.RegelKommuniceringController;
import se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.GetDataResponse;
import se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.PatchErsattningRequest;

@Path("/regel/kommunicering")
@ApplicationScoped
public class KommuniceringController extends RegelKommuniceringController<GetDataResponse, PatchErsattningRequest>
{
}
