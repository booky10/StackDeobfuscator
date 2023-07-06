const input = document.getElementById("input");
const status = document.getElementById("status");
const message = document.getElementById("message");

const copyButton = document.getElementById("copy");
const deobfButton = document.getElementById("deobfuscate");

function deobfuscate() {
    input.disabled = true;
    status.innerText = "Deobfuscating...";
    message.innerText = "";

    copyButton.disabled = true;
    deobfButton.disabled = true;

    const req = new XMLHttpRequest();
    req.open("POST", `${location.href}api/v1/deobf/body`, true);
    req.onreadystatechange = () => {
        if (req.readyState != 4) {
            return;
        }

        input.disabled = false;
        copyButton.disabled = false;
        deobfButton.disabled = false;

        const totalTime = (req.getResponseHeader("Total-Time") / 1000000).toFixed(2);

        if (req.status != 200) {
            status.innerText = `Error ${req.status} while deobfuscating${totalTime > 0 ? ` (took ${totalTime}ms)` : ""}:`;
            message.innerText = req.responseText;
            return;
        }

        const mappingsTime = (req.getResponseHeader("Mappings-Time") / 1000000).toFixed(2);
        const remapTime = (req.getResponseHeader("Remap-Time") / 1000000).toFixed(2);

        status.innerText = `Deobfuscating took ${totalTime}ms (${mappingsTime}ms creating mappings, ${remapTime}ms remapping)`;
        input.value = req.responseText;
    };

    req.setRequestHeader("Content-Type", "text/plain");
    req.setRequestHeader("Accept", "text/plain");
    req.send(input.value);
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

deobfButton.onclick = (event) => deobfuscate();
