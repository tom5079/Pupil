async function get_gallery_block(galleryid) {
    const uri = `//${domain}/${galleryblockdir}/${galleryid}${galleryblockextension}`;

    const response = await fetch(uri);
    const html = await response.text();

    const doc = new DOMParser().parseFromString(rewrite_tn_paths(html), 'text/html');

    const title_elm = doc.querySelector('h1 > a');

    const gallery_url = title_elm.href;
    const title = title_elm.textContent;

    const thumbnails = Array.from(doc.querySelectorAll('.dj-img-cont img'), elm => `https:${elm.getAttribute('data-src')}`);

    const artists = Array.from(doc.querySelectorAll('.artist-list a'), elm => elm.innerText);
    const series = Array.from(doc.querySelectorAll('a[href^="/series/"]'), elm => elm.innerText);
    const type = doc.querySelector('a[href^="/type/"]').innerText;

    const language = doc.querySelector('a[href^="/index"][href$=".html"]').getAttribute('href').slice(7, -5);

    const related_tags = Array.from(doc.querySelectorAll('.relatedtags a'), elm => decodeURIComponent(elm.getAttribute('href')).slice(5, -9));

    return {
        id: galleryid,
        galleryUrl: gallery_url,
        thumbnails: thumbnails,
        title: title,
        artists: artists,
        series: series,
        type: type,
        language: language,
        relatedTags: related_tags
    };
}