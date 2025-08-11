const core = require('@actions/core');
const github = require('@actions/github');
const fs = require('fs');
const argv = require('minimist')(process.argv.slice(2));

const { runTests } = require("./testserver-utils");
const { generateLocalFile, generateDefaultFile } = require("./local-files-utils");
const { resetTestData, createExpectedOutcomes, extractDid, extractMethodName, getWorkingMethods, getWorkingUrls } = require("./utils");

const testDataSkeleton = {
    implementation: 'Universal Resolver',
    implementer: 'Decentralized Identity Foundation and Contributors',
    didMethod: '',
    expectedOutcomes: {
        defaultOutcomes: [],
        invalidDidErrorOutcome: [],
        notFoundErrorOutcome: [],
        representationNotSupportedErrorOutcome: [],
        deactivatedOutcome: []
    },
    executions: []
}

const getOutputPath = () => {
    return argv["OUTPUT_PATH"] !== undefined ? argv["OUTPUT_PATH"] : './'
}

/* Set mode of test results
*  LOCAL => local files are created for manual upload
*  SERVER => tests are run against a hosted testserver
* */
const getMode = () => {
    if (argv["MODE"] !== undefined) {
        return argv["MODE"].toUpperCase();
    } else {
        return "SERVER";
    }
}

const getTestset = () => {
    if (argv["DRIVER_STATUS_REPORT"] !== undefined) {
        return argv["DRIVER_STATUS_REPORT"];
    } else {
        return core.getInput('DRIVER_STATUS_REPORT');
    }
}

const getHost = () => {
    if (argv["HOST"] !== undefined) {
        return argv["HOST"];
    } else {
        return core.getInput('host');
    }
}

const getShouldGenerateDefaultFile = () => {
    if (argv["GENERATE_DEFAULT_FILE"] !== undefined) {
        return JSON.parse(argv["GENERATE_DEFAULT_FILE"].toLowerCase())
    } else {
        return false;
    }
}

const DEBUG = process.env.DEBUG === 'true';

function debugLog(...args) {
    if (DEBUG) {
        console.log(...args);
    }
}

try {
    // Get the JSON webhook payload for the event that triggered the workflow
    const payload = JSON.stringify(github.context.payload, undefined, 2)
    debugLog(`The event payload: ${payload}`);  // Fix Issue #558

    // Input variables
    const mode = getMode();
    debugLog(`Running in ${mode} mode`);        // Fix Issue #559

    // LOCAL mode env variables
    const outputPath = getOutputPath();
    debugLog(`Output path for testfiles ${outputPath}`);    // Fix Issue #560
    const shouldGenerateDefaultFile = getShouldGenerateDefaultFile();

    // SERVER mode env variables
    const host = getHost();
    debugLog(`Testserver host ${host}`);    // Fix Issue #561

    // Common testset
    const testSet = getTestset();
    debugLog(`Running with testSet: ${testSet}`);   // Fix Issue #562

    const rawData = fs.readFileSync(testSet);
    const resolutionResults = JSON.parse(rawData);

    const workingMethods = getWorkingMethods(resolutionResults);
    debugLog('Working methods', workingMethods);    // Fix Issue #563

    const workingUrls = getWorkingUrls(resolutionResults);
    debugLog('Working Urls', workingUrls);      // Fix Issue #564

    const resolvers = [];

    workingMethods.forEach(workingMethodName => {
        const testData = {...testDataSkeleton};
        resetTestData(testData);
        testData.didMethod = `did:${workingMethodName}`;

        let index = 0;
        workingUrls.forEach(url => {
            if (workingMethodName === extractMethodName(extractDid(url))) {
                createExpectedOutcomes(testData, resolutionResults[url], index)

                testData.executions.push({
                    function: 'resolveRepresentation',
                    input: {
                        did: resolutionResults[url].resolutionResponse["application/did+ld+json"].didDocument.id,
                        resolutionOptions: {
                            accept: "application/did+ld+json"
                        }
                    },
                    output: {
                        didResolutionMetadata: resolutionResults[url].resolutionResponse["application/did+ld+json"].didResolutionMetadata,
                        didDocumentStream: JSON.stringify(resolutionResults[url].resolutionResponse["application/did+ld+json"].didDocument),
                        didDocumentMetadata: resolutionResults[url].resolutionResponse["application/did+ld+json"].didDocumentMetadata
                    }
                })
                index++;
            }
        });

        if (mode === "LOCAL") generateLocalFile(testData, workingMethodName, outputPath);
        if (mode === "SERVER") resolvers.push(testData);
    })

    if (mode === "LOCAL" && shouldGenerateDefaultFile === true) {
        generateDefaultFile(outputPath)
    }

    if (mode === "SERVER") {
        runTests(resolvers, host, outputPath);
    }
} catch (error) {
    core.setFailed(error.message);
}