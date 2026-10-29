package se.fk.github.kommunicering.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import se.fk.github.kommunicering.logic.exception.ErsattningSerializationException;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;

@Provider
public class ErsattningSerializationExceptionMapper implements ExceptionMapper<ErsattningSerializationException>
{
   @Override
   public Response toResponse(ErsattningSerializationException exception)
   {
      var responseStatus = Response.Status.INTERNAL_SERVER_ERROR;

      ErrorResponse errorResponse = new ErrorResponse();
      errorResponse.setCode(responseStatus.getStatusCode());
      errorResponse.setMessage("Failed to create updated ersattning");

      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();
   }
}
