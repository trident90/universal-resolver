package uniresolver.examples;

import uniresolver.driver.did.sov.DidSovDriver;
import uniresolver.local.LocalUniDereferencer;
import uniresolver.local.LocalUniResolver;
import uniresolver.result.DereferenceResult;

import java.util.HashMap;
import java.util.Map;

public class TestLocalUniDereferencer {

	public static void main(String[] args) throws Exception {

		LocalUniResolver uniResolver = new LocalUniResolver();
		uniResolver.getDrivers().add(new DidSovDriver());
		// Fix Issue #539, #540, #541
		DidSovDriver didSovDriver = uniResolver.getDriver(DidSovDriver.class);
		if (didSovDriver != null) {
			// Fix Issue #544
			if (didSovDriver.getLibIndyPath() != null) {
				didSovDriver.setLibIndyPath("./sovrin/lib/libindy.so");
			}
			didSovDriver.setPoolConfigs("_;./sovrin/mainnet.txn");
			didSovDriver.setPoolVersions("_;2");
		}

		LocalUniDereferencer uniDereferencer = new LocalUniDereferencer();
		uniDereferencer.setUniResolver(uniResolver);

		Map<String, Object> dereferenceOptions = new HashMap<>();
		dereferenceOptions.put("accept", "application/did+ld+json");
		DereferenceResult dereferenceResult = uniDereferencer.dereference("did:sov:WRfXPg8dantKVubE3HX8pw#key-1", dereferenceOptions);
		System.out.println(dereferenceResult.toJson());
	}
}
