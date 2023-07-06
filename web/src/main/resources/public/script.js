const input = document.getElementById("input");
const urlInput = document.getElementById("urlinput");

const status = document.getElementById("status");
const message = document.getElementById("message");

const copyButton = document.getElementById("copy");
const deobfButton = document.getElementById("deobfuscate");

const mappingsSelect = document.getElementById("mappings");
const versionSelect = document.getElementById("version");
const environmentSelect = document.getElementById("environment");

// fixes the input url by trying to get the raw content instead of the html webpage
function tryFixInputUrl() {
    if (urlInput.value.length == 0) {
        return;
    }

    const url = new URL(urlInput.value);
    if (url.host.endsWith("mclo.gs") && url.host !== "api.mclo.gs") {
        url.host = "api.mclo.gs";
        url.pathname = "/1/raw" + url.pathname;
    } else if (url.host.endsWith("pastes.dev") && url.host !== "api.pastes.dev") {
        // can't just remap to raw pathname, like all other paste sites
        // swapping hosts is just as easy though
        url.host = "api.pastes.dev";
    } else if (url.host.endsWith("paste.ee")) {
        // getting the raw text from paste.ee is as simple as swapping "/p/<id>" for "/d/<id>"
        if (!url.pathname.startsWith("/d") && url.pathname.startsWith("/p")) {
            url.pathname = "/d" + url.pathname.substring("/p".length);
        }
    } else if (
            // all haste-servers (before being bought by toptal) and pastebin
            // support simply adding "/raw" at the front of the path
            (url.host.startsWith("haste") || url.host.startsWith("paste"))
            // after toptal bought hastebin.com they completely destroyed the ability to
            // change the pathname to get the raw file content... thanks?
            && !url.host.endsWith("toptal.com") && !url.host.endsWith("hastebin.com")
            // same for paste.gg, only that they weren't bought by toptal and support multiple files
            && !url.host.endsWith("paste.gg")) {
        if (!url.pathname.startsWith("/raw")) {
            url.pathname = "/raw" + url.pathname;
        }
    }
    // don't want to add support for github gist, too complicated with multiple files

    urlInput.value = url.toString();
}

function loadVersions() {
    const req = new XMLHttpRequest();
    req.open("GET", "/mc_versions.json", true);
    req.onreadystatechange = () => {
        if (req.readyState != 4) {
            return;
        }

        const versions = JSON.parse(req.responseText);
        versions.forEach((version, index) => {
            const option = document.createElement("option");
            option.value = version.world_version;
            option.innerText = version.name;
            versionSelect.appendChild(option);
        });
        versionSelect.remove(0);
        console.log(`Added ${versions.length} possible minecraft versions`)
    };

    req.setRequestHeader("Accept", "application/json");
    req.send();
}

function deobfuscate() {
    tryFixInputUrl();

    const mappings = mappingsSelect.options[mappingsSelect.selectedIndex].value;
    const version = versionSelect.options[versionSelect.selectedIndex].value;
    const environment = environmentSelect.options[environmentSelect.selectedIndex].value;

    status.innerText = "Deobfuscating...";
    message.innerText = "";

    input.disabled = true;
    urlInput.disabled = true;
    copyButton.disabled = true;
    deobfButton.disabled = true;
    mappingsSelect.disabled = true;
    versionSelect.disabled = true;
    environmentSelect.disabled = true;

    const req = new XMLHttpRequest();
    req.onreadystatechange = () => {
        if (req.readyState != 4) {
            return;
        }

        input.disabled = false;
        urlInput.disabled = false;
        copyButton.disabled = false;
        deobfButton.disabled = false;
        mappingsSelect.disabled = false;
        versionSelect.disabled = false;
        environmentSelect.disabled = false;

        const totalTime = (req.getResponseHeader("Total-Time") / 1000000).toFixed(2);

        if (req.status != 200) {
            status.innerText = `Error ${req.status} while deobfuscating${totalTime > 0 ? ` (took ${totalTime}ms)` : ""}:`;
            message.innerText = req.responseText;
            return;
        }

        let urlTime = req.getResponseHeader("Url-Time");
        let urlTimeStr = "";
        if (urlTime) {
            urlTime = (urlTime / 1000000).toFixed(2);
            urlTimeStr = `${urlTime}ms getting url, `;
        }

        const mappingsTime = (req.getResponseHeader("Mappings-Time") / 1000000).toFixed(2);
        const remapTime = (req.getResponseHeader("Remap-Time") / 1000000).toFixed(2);

        status.innerText = `Deobfuscating took ${totalTime}ms (${mappingsTime}ms creating mappings, ${urlTimeStr}${remapTime}ms remapping)`;
        input.value = req.responseText;
    };

    const setHeaders = () => {
        // these require an open request, so just do this
        req.setRequestHeader("Content-Type", "text/plain");
        req.setRequestHeader("Accept", "text/plain");
    };

    const reqParams = `mappings=${mappings}&version=${version}&environment=${environment}`;
    if (urlInput.value.length == 0) {
        req.open("POST", `/api/v1/deobf/body?${reqParams}`, true);
        setHeaders();
        req.send(input.value);
    } else {
        const url = encodeURIComponent(urlInput.value);
        req.open("GET", `/api/v1/deobf/url?${reqParams}&url=${url}`, true);
        setHeaders();
        req.send();
    }
}

copyButton.onclick = () => {
    input.focus();
    input.select();

    if (navigator.clipboard) {
        navigator.clipboard.writeText(input.value);
    } else {
        // fallback
        document.execCommand("copy");
    }
};

loadVersions();
deobfButton.onclick = (event) => deobfuscate();
