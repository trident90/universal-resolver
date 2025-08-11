package uniresolver.driver.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import foundation.identity.did.DID;
import foundation.identity.did.DIDURL;
import foundation.identity.did.representations.Representations;
import foundation.identity.did.representations.production.RepresentationProducer;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import uniresolver.DereferencingException;
import uniresolver.ResolutionException;
import uniresolver.driver.util.HttpBindingServerUtil;
import uniresolver.driver.util.MediaTypeUtil;
import uniresolver.result.DereferenceResult;
import uniresolver.result.ResolveResult;
import uniresolver.result.Result;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ResolveServlet extends HttpServlet implements Servlet {

	private static final Logger log = LoggerFactory.getLogger(ResolveServlet.class);

	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

		// read request

		request.setCharacterEncoding("UTF-8");
		response.setCharacterEncoding("UTF-8");

		String contextPath = request.getContextPath();
		String servletPath = request.getServletPath();
		String requestPath = request.getRequestURI();

		if (log.isDebugEnabled()) log.debug("Incoming request: " + requestPath);

		String path = requestPath.substring(contextPath.length() + servletPath.length());
		if (path.startsWith("/")) path = path.substring(1);

		if (path.isEmpty()) {
			ServletUtil.sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, "No identifier found in request.");
			return;
		}

		// parse request

		String identifier;
		Map<String, Object> options = new LinkedHashMap<>();
		boolean isResolve;

		if (path.startsWith("did%3A")) {
			identifier = URLDecoder.decode(path, StandardCharsets.UTF_8);
			if (request.getQueryString() != null) {
				if (request.getQueryString().contains("=")) {
					for (Enumeration<String> e = request.getParameterNames(); e.hasMoreElements(); ) {
						String parameterName = e.nextElement();
						String parameterValue = request.getParameter(parameterName);
						options.put(parameterName, parameterValue);
					}
				} else {
					options = objectMapper.readValue(URLDecoder.decode(request.getQueryString(), StandardCharsets.UTF_8), LinkedHashMap.class);
				}
			} else if (request.getQueryString() != null) {
				options.putAll(objectMapper.readValue(request.getQueryString(), Map.class));
			}
		} else {
			identifier = path;
			if (request.getQueryString() != null) identifier += "?" + request.getQueryString();
		}
		isResolve = (! identifier.contains("/")) && (! identifier.contains("?")) && (! identifier.contains("#"));

		if (log.isInfoEnabled()) log.info("Incoming identifier: " + identifier + " (isResolve=" + isResolve + "), incoming options: " + options);

		// prepare options

		String httpXConfigHeader = request.getHeader("X-Config");
		if (log.isInfoEnabled()) log.info("Incoming X-Config: header string: " + httpXConfigHeader);
		Map<String, Object> httpXConfigHeaderMap = httpXConfigHeader == null ? null : (Map<String, Object>) objectMapper.readValue(httpXConfigHeader, Map.class);
		if (httpXConfigHeaderMap != null) options.put("_http_x_config", httpXConfigHeaderMap);

		String httpAcceptHeader = request.getHeader("Accept");
		if (log.isInfoEnabled()) log.info("Incoming Accept: header string: " + httpAcceptHeader);

		List<MediaType> httpAcceptMediaTypes = httpAcceptHeader == null ? null : MediaType.parseMediaTypes(httpAcceptHeader);
		if (httpAcceptMediaTypes != null) MimeTypeUtils.sortBySpecificity(httpAcceptMediaTypes);
		if (httpAcceptHeader != null) options.put("_http_accept", httpAcceptMediaTypes);

		if (! options.containsKey("accept")) {
			if (isResolve) {
				if (httpAcceptMediaTypes == null) httpAcceptMediaTypes = Collections.singletonList(MediaType.parseMediaType(ResolveResult.MEDIA_TYPE));
				options.put("accept", HttpBindingServerUtil.resolveAcceptForHttpAccepts(httpAcceptMediaTypes));
			} else {
				if (httpAcceptMediaTypes == null) httpAcceptMediaTypes = Collections.singletonList(MediaType.parseMediaType(DereferenceResult.MEDIA_TYPE));
				options.put("accept", HttpBindingServerUtil.dereferenceAcceptForHttpAccepts(httpAcceptMediaTypes));
			}
		}

		if (log.isDebugEnabled()) log.debug("Using options: " + options);

		// execute the request

		Result result;

		if (isResolve) {
			// Fix Issue #547
			try {
				result = InitServlet.getDriver().resolve(DID.fromString(identifier), options);
				if (result == null) throw new ResolutionException(DereferencingException.ERROR_NOTFOUND, "No resolve result for " + identifier);
			} catch (ResolutionException rex) {
				if (log.isWarnEnabled()) log.warn("ResolutionException for " + identifier + ": " + rex.getMessage(), rex);
				result = rex.toErrorResolveResult();
			} catch (IllegalArgumentException iaex) {
				if (log.isWarnEnabled()) log.warn("IllegalArgumentException for " + identifier + ": " + iaex.getMessage(), iaex);
				result = new ResolutionException("Invalid identifier: " + identifier + ", " + iaex.getMessage(), iaex).toErrorResolveResult();
			} catch (IOException ioex) {
				if (log.isWarnEnabled()) log.warn("IOException for " + identifier + ": " + ioex.getMessage(), ioex);
				result = new ResolutionException("IO error while resolving " + identifier + ": " + ioex.getMessage(), ioex).toErrorResolveResult();
			} catch (Exception ex) {
				if (log.isWarnEnabled()) log.warn("Unknown resolve problem for " + identifier + ": " + ex.getMessage(), ex);
				result = new ResolutionException("Resolve problem for " + identifier + ": " + ex.getMessage(), ex).toErrorResolveResult();
			}
		} else {
			// Fix Issue #548
			try {
				result = InitServlet.getDriver().dereference(DIDURL.fromString(identifier), options);
				if (result == null) throw new DereferencingException(DereferencingException.ERROR_NOTFOUND, "No dereference result for " + identifier);
			} catch (ResolutionException rex) {
				if (log.isWarnEnabled()) log.warn("ResolutionException for " + identifier + ": " + rex.getMessage(), rex);
				DereferencingException dex = new DereferencingException(rex.getError(), "Error " + rex.getError() + " from resolver: " + rex.getMessage(), rex.getDidResolutionMetadata(), rex);
				result = dex.toErrorDereferenceResult();
			} catch (IllegalArgumentException iaex) {
				if (log.isWarnEnabled()) log.warn("IllegalArgumentException for " + identifier + ": " + iaex.getMessage(), iaex);
				DereferencingException dex = new DereferencingException("Invalid identifier: " + identifier + ", " + iaex.getMessage(), iaex);
				result = dex.toErrorDereferenceResult();
			} catch (IOException ioex) {
				if (log.isWarnEnabled()) log.warn("IOException for " + identifier + ": " + ioex.getMessage(), ioex);
				DereferencingException dex = new DereferencingException("IO error while dereferencing " + identifier + ": " + ioex.getMessage(), ioex);
				result = dex.toErrorDereferenceResult();
			} catch (DereferencingException dex) {
				if (log.isWarnEnabled()) log.warn("DereferencingException for " + identifier + ": " + dex.getMessage(), dex);
				result = dex.toErrorDereferenceResult();
			} catch (Exception ex) {
				if (log.isWarnEnabled()) log.warn("Unknown dereference problem for " + identifier + ": " + ex.getMessage(), ex);
				DereferencingException dex = new DereferencingException("Dereference problem for " + identifier + ": " + ex.getMessage(), ex);
				result = dex.toErrorDereferenceResult();
			}
		}

		if (log.isInfoEnabled()) log.info("Result for " + identifier + ": " + result);

		// experimental: support for redirect

		if (Integer.valueOf(HttpServletResponse.SC_SEE_OTHER).equals(result.getFunctionMetadata().get("_http_code"))) {
			Map<String, String> httpHeaders = (Map<String, String>) result.getFunctionMetadata().get("_http_headers");
			if (log.isDebugEnabled()) log.debug("Redirecting with HTTP headers: " + httpHeaders);
			ServletUtil.sendResponse(
					response,
					HttpServletResponse.SC_SEE_OTHER,
					httpHeaders,
					null);
			return;
		}

		// determine status code, content type, body

		int httpStatusCode = HttpBindingServerUtil.httpStatusCodeForResult(result);
		String httpContentType = HttpBindingServerUtil.httpContentTypeForResult(result);
		Object httpBody = HttpBindingServerUtil.httpBodyForResult(result);

		// write error result

		if (result.isErrorResult()) {
			if (log.isDebugEnabled()) log.debug("Ignoring media type, returning error result");
			ServletUtil.sendResponse(
					response,
					httpStatusCode,
					httpContentType,
					(String) httpBody);
			return;
		}

		// write result using content negotiation

		for (MediaType httpAcceptMediaType : httpAcceptMediaTypes) {

			if (result instanceof ResolveResult && MediaTypeUtil.isMediaTypeAcceptable(httpAcceptMediaType, ResolveResult.MEDIA_TYPE)) {
				if (log.isDebugEnabled()) log.debug("Supporting HTTP media type " + httpAcceptMediaType + " via default resolve result content type " + ResolveResult.MEDIA_TYPE);
				ServletUtil.sendResponse(
						response,
						httpStatusCode,
						httpContentType,
						(String) httpBody);
				return;
			}

			if (result instanceof DereferenceResult && MediaTypeUtil.isMediaTypeAcceptable(httpAcceptMediaType, DereferenceResult.MEDIA_TYPE)) {
				if (log.isDebugEnabled()) log.debug("Supporting HTTP media type " + httpAcceptMediaType + " via default dereference result content type " + DereferenceResult.MEDIA_TYPE);
				ServletUtil.sendResponse(
						response,
						httpStatusCode,
						httpContentType,
						(String) httpBody);
				return;
			}

			// determine representation media type

			if (result instanceof ResolveResult resolveResult) {
				for (RepresentationProducer representationProducer : Representations.representationProducers) {
					if (MediaTypeUtil.isMediaTypeAcceptable(httpAcceptMediaType, representationProducer.getMediaType())) {
						if (log.isDebugEnabled()) log.debug("Supporting HTTP media type " + httpAcceptMediaType + " via DID document media type " + representationProducer.getMediaType() + " and resolved DID document media type " + representationProducer.getMediaType());
						httpContentType = representationProducer.getMediaType();
						httpBody = representationProducer.produce(resolveResult.getDidDocument());
						ServletUtil.sendResponse(
								response,
								httpStatusCode,
								httpContentType,
								(byte[]) httpBody);
						return;
					}
				}
			}

			if (result instanceof DereferenceResult dereferenceResult) {
				if (result.getContentType() != null && MediaTypeUtil.isMediaTypeAcceptable(httpAcceptMediaType, result.getContentType())) {
					if (log.isDebugEnabled()) log.debug("Supporting HTTP media type " + httpAcceptMediaType + " via content media type " + result.getContentType());
					httpContentType = dereferenceResult.getContentType();
					httpBody = result.getFunctionContent();
					ServletUtil.sendResponse(
							response,
							httpStatusCode,
							httpContentType,
							(byte[]) httpBody);
					return;
				}
			}

			// continue

			if (log.isDebugEnabled()) log.debug("Not supporting HTTP media type " + httpAcceptMediaType);
		}

		// not acceptable

		ServletUtil.sendResponse(
				response,
				HttpServletResponse.SC_NOT_ACCEPTABLE,
				"Not acceptable media types " + httpAcceptHeader);
	}
}
