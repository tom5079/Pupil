function get_gallery_info(galleryID) {
    return new Promise((resolve, reject) => {
        $.getScript(`https://ltn.hitomi.la/galleries/${galleryID}.js`, () => {
            resolve(galleryinfo);
        });
    });
}

function do_search(query) {
    let terms = query.toLowerCase().trim().split(/\s+/);
    let positive_terms = [], negative_terms = [];

    $.each(terms, function (i, term) {
        term = term.replace(/_/g, ' ');
        if (term.match(/^-/)) {
            negative_terms.push(term.replace(/^-/, ''));
        } else {
            positive_terms.push(term);
        }
    });


    return new Promise((resolve, reject) => { //first results
        if (!positive_terms.length) {
            get_galleryids_from_nozomi(undefined, 'index', 'all').then(results => {
                resolve(results);
            });
        } else {
            const term = positive_terms.shift();
            get_galleryids_for_query(term).then(results => {
                resolve(results);
            });
        }
    }).then(() => { //positive results
        return Promise.all(positive_terms.map(term => {
            return new Promise((resolve, reject) => {
                get_galleryids_for_query(term).then(new_results => {
                    const new_results_set = new Set(new_results);
                    results = results.filter(galleryid => new_results_set.has(galleryid));
                    resolve();
                })
            });
        }));
    }).then(() => { //negative results
        return Promise.all(negative_terms.map(term => {
            return new Promise((resolve, reject) => {
                get_galleryids_for_query(term).then(new_results => {
                    const new_results_set = new Set(new_results);
                    results = results.filter(galleryid => !new_results_set.has(galleryid));
                    resolve();
                });
            });
        }));
    }).then(() => {
        const final_results_length = results.length;
        $('#number-of-results').html(final_results_length);
        if (!final_results_length) {
            hide_loading();
            $('.gallery-content').html($('#no-results-content').html());
        } else {
            put_results_on_page();
        }
    });
}

function replace_jpg_tn(tn) {
    if (!tn.startsWith('https')) tn = `https:${tn}`;
    if (tn.endsWith('jpg')) tn = tn.replace('bigtn', 'webpbigtn').replace(/jpg$/, 'webp');

    return tn;
}