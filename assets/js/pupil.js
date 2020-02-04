'use strict';

const LATEST_URL = "https://api.github.com/repos/tom5079/Pupil/releases/latest";

function getLatestRelease() {
    return new Promise(function(resolve, reject) {
        const xhr = new XMLHttpRequest();

        xhr.open("GET", LATEST_URL)
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    resolve(JSON.parse(xhr.responseText))
                } else {
                    reject(new Error('XHR failed on getLAtestRelease() with status ' + xhr.status))
                }
            }
        };

        xhr.send();
    })
}