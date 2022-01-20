async function get_gallery_url(galleryid) {
    const url = `https://hitomi.la/galleries/${galleryid}.html`;

    const response = await fetch(url);
    const html = await response.text();

    const doc = new DOMParser().parseFromString(html, 'text/html');

    return doc.querySelector('link').getAttribute('href');
}

async function get_gallery(galleryid) {
    const url = await get_gallery_url(galleryid);

    const response = await fetch(url);
    const html = await response.text();

    const doc = new DOMParser().parseFromString(rewrite_tn_paths(html), 'text/html');

    //related
    eval(Array.from(doc.getElementsByTagName('script')).find(elm => elm.innerHTML.includes('var related')).innerHTML);

    const lang_list = {};

    Array.from(doc.querySelectorAll('#lang-list a')).forEach(elm => lang_list[elm.innerText] = elm.getAttribute('href').slice(11, -5));

    const cover = doc.querySelector('.cover img').src.replace('bigtn', 'webpbigtn').replace(/.jpg$/, '.webp');

    const title = doc.querySelector('.gallery h1 a').innerText;
    const artists = Array.from(doc.querySelectorAll('.gallery h2 a'), elm => elm.innerText);
    const groups = Array.from(doc.querySelectorAll('.gallery-info a[href^="/group."]'), elm => elm.innerText);
    const type = doc.querySelector('.gallery-info a[href^="/type/"]').innerText.trim();

    const language = doc.querySelector('.gallery-info a[href^="/index"][href$=".html"]').getAttribute('href').slice(7, -5);

    const series = Array.from(doc.querySelectorAll('.gallery-info a[href^="/series/"]'), elm => elm.innerText);
    const characters = Array.from(doc.querySelectorAll('.gallery-info a[href^="/character/"]'), elm => elm.innerText);

    const tags = Array.from(doc.querySelectorAll('.gallery-info a[href^="/tag/"]'), elm => decodeURIComponent(elm.getAttribute('href')).slice(5, -9));

    const gallery_info = await get_gallery_info(galleryid);
    const thumbnails = gallery_info.files.map(file => url_from_url_from_hash(galleryid, file, 'webpsmallsmalltn', 'webp', 'tn'));

    return {
        related: related,
        langList: lang_list,
        cover: cover,
        title: title,
        artists: artists,
        groups: groups,
        type: type,
        language: language,
        series: series,
        characters: characters,
        tags: tags,
        thumbnails: thumbnails
    };
}