package org.middleware.resource;

import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.middleware.dto.ApiResponse;
import org.middleware.service.ExcelMarkdownExtractor;
import org.middleware.service.ExcelMarkdownExtractor.ExcelExtractionResult;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/ai")
@Produces(MediaType.APPLICATION_JSON)
public class AiExcelResource {

    private static final Logger LOG = Logger.getLogger(AiExcelResource.class.getName());
    private static final String XLSX_REQUIRED_MESSAGE = "Le fichier doit etre un fichier Excel .xlsx";

    @Inject
    ExcelMarkdownExtractor excelMarkdownExtractor;

    @POST
    @Path("/excel-context")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({"ADMIN", "USER"})
    public Response extractExcelContext(@FormParam("file") FileUpload file) {
        try {
            if (file == null || file.filePath() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("FILE_REQUIRED", "Aucun fichier n'a ete fourni"))
                    .build();
            }

            String fileName = file.fileName();
            if (!isXlsxFileName(fileName)) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("INVALID_FILE_TYPE", XLSX_REQUIRED_MESSAGE))
                    .build();
            }

            ExcelExtractionResult result = excelMarkdownExtractor.extract(file.filePath(), fileName);
            return Response.ok(result).build();
        } catch (Exception e) {
            if (isInvalidExcelException(e)) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("INVALID_FILE_TYPE", XLSX_REQUIRED_MESSAGE))
                    .build();
            }
            LOG.log(Level.SEVERE, "Erreur extraction contexte Excel", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("EXCEL_CONTEXT_ERROR", "Erreur lors de la lecture du fichier Excel"))
                .build();
        }
    }

    private boolean isXlsxFileName(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }

    private boolean isInvalidExcelException(Exception e) {
        return e instanceof IOException
            || e instanceof org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException
            || e instanceof org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException
            || e instanceof org.apache.poi.openxml4j.exceptions.InvalidFormatException
            || e instanceof org.apache.poi.ooxml.POIXMLException;
    }
}
