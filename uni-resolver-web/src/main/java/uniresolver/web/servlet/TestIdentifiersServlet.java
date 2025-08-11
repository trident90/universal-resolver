package uniresolver.web.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uniresolver.UniResolver;
import uniresolver.web.WebUniResolver;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class TestIdentifiersServlet extends WebUniResolver {

	protected static final Logger log = LoggerFactory.getLogger(TestIdentifiersServlet.class);

	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

		// read request

		request.setCharacterEncoding("UTF-8");
		response.setCharacterEncoding("UTF-8");

		if (log.isInfoEnabled()) log.info("Incoming request.");

		// execute the request

		Map<String, List<String>> testIdentifiers;
		String testIdentifiersString;

		// Fix Issue #556
		try {
			testIdentifiers = this.testIdentifiers();
			testIdentifiersString = testIdentifiers == null ? null : objectMapper.writeValueAsString(testIdentifiers);
		} catch (IOException ioex) {
			if (log.isWarnEnabled()) log.warn("IOException while retrieving test identifiers: " + ioex.getMessage(), ioex);
			ServletUtil.sendResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "IOException while retrieving test identifiers: " + ioex.getMessage());
			return;
		} catch (IllegalArgumentException iaex) {
			if (log.isWarnEnabled()) log.warn("IllegalArgumentException while retrieving test identifiers: " + iaex.getMessage(), iaex);
			ServletUtil.sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, "IllegalArgumentException while retrieving test identifiers: " + iaex.getMessage());
			return;
		} catch (Exception ex) {
			if (log.isWarnEnabled()) log.warn("Unknown exception while retrieving test identifiers: " + ex.getMessage(), ex);
			ServletUtil.sendResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Resolver reported: " + ex.getMessage());
			return;
		}

		if (log.isInfoEnabled()) log.info("Test identifiers: " + testIdentifiers);

		// no result?

		if (testIdentifiers == null) {
			ServletUtil.sendResponse(response, HttpServletResponse.SC_NOT_FOUND, "No test identifiers.");
			return;
		}

		// write result

		ServletUtil.sendResponse(response, HttpServletResponse.SC_OK, UniResolver.TEST_IDENTIFIER_MEDIA_TYPE, testIdentifiersString);
	}
}