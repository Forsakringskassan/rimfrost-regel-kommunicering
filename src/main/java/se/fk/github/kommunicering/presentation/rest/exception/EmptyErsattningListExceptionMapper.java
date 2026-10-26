package se.fk.github.kommunicering.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import se.fk.github.kommunicering.logic.exception.EmptyErsattningListException;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;

@Provider
public class EmptyErsattningListExceptionMapper implements ExceptionMapper<EmptyErsattningListException>
{
   @Override
   public Response toResponse(EmptyErsattningListException exception)
   {
      var responseStatus = Response.Status.BAD_REQUEST;

      var errorResponse = new ErrorResponse();
      errorResponse.setCode(responseStatus.getStatusCode());
      errorResponse.setMessage("No updated ersattning provided");

      return Response.status(responseStatus).entity(errorResponse).build();
   }
}
