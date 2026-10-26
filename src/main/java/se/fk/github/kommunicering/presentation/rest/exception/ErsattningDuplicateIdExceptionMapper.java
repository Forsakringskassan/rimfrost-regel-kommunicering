package se.fk.github.kommunicering.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import se.fk.github.kommunicering.logic.exception.ErsattningDuplicateIdException;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;

@Provider
public class ErsattningDuplicateIdExceptionMapper implements ExceptionMapper<ErsattningDuplicateIdException>
{
   @Override
   public Response toResponse(ErsattningDuplicateIdException exception)
   {
      var responseStatus = Response.Status.BAD_REQUEST;

      var errorResponse = new ErrorResponse();
      errorResponse.setCode(responseStatus.getStatusCode());
      errorResponse.setMessage("Failed to process request due to duplicate ersattning ID");

      return Response.status(responseStatus).entity(errorResponse).build();
   }
}
