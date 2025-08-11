const fs = require("fs")

const generateLocalFile = (testData, methodName, path) => {
    fs.writeFileSync(
        `${path}/universal-resolver-did-${methodName}.json`,
        JSON.stringify(testData, null, 4)
    );
};

const generateDefaultFile = (path) => {
    fs.readdir(path, (err, files) => {

        const resolvers = []

        files.forEach(file => {
            debugLog(file); // Fix Issue #565
            const fileContent = JSON.parse(fs.readFileSync(`${path}/${file}`));
            debugLog(fileContent);  // Fix Issue #566
            if (file.startsWith('universal-resolver') || file.startsWith('resolver')) {
                resolvers.push(`require('../implementations/${file}')`)
            }
        });
        debugLog(resolvers); // Fix Issue #567

        fs.writeFileSync(
            '/Users/devfox/testsuites/did-test-suite/packages/did-core-test-server/suites/did-resolution/default.js',
            `module.exports = {
            name: '7.1 DID Resolution',
            resolvers: [${resolvers}]
        }`
        );
    });
};

module.exports = {
    generateDefaultFile,
    generateLocalFile
}
