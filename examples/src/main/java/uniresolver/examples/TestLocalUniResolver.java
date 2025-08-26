package uniresolver.examples;

import uniresolver.driver.did.sov.DidSovDriver;
import uniresolver.local.LocalUniResolver;
import uniresolver.result.ResolveResult;

import java.util.HashMap;
import java.util.Map;

public class TestLocalUniResolver {

	public static void main(String[] args) throws Exception {

		// Fix Issue 837, 838, 839
		LocalUniResolver uniResolver = new LocalUniResolver();
		uniResolver.getDrivers().add(new DidSovDriver());
		DidSovDriver didSovDriver = uniResolver.getDriver(DidSovDriver.class);
		if (didSovDriver != null) {
		    didSovDriver.setLibIndyPath("./sovrin/lib/libindy.so");
		    didSovDriver.setPoolConfigs("_;./sovrin/mainnet.txn");
		    didSovDriver.setPoolVersions("_;2");
		} else {
		    System.err.println("DidSovDriver is not available.");
		}

		Map<String, Object> resolveOptions = new HashMap<>();
		resolveOptions.put("accept", "application/did+ld+json");

		ResolveResult resolveResult;
		resolveResult = uniResolver.resolve("did:sov:WRfXPg8dantKVubE3HX8pw", resolveOptions);
		System.out.println(resolveResult.toJson());
		resolveResult = uniResolver.resolveRepresentation("did:sov:WRfXPg8dantKVubE3HX8pw", resolveOptions);
		System.out.println(resolveResult.toJson());
	}
}
