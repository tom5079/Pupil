function get_gallery_info(galleryID) {
    return new Promise((resolve, reject) => {
        $.getScript(`https://ltn.hitomi.la/galleries/${galleryID}.html`, () => {
            resolve(galleryinfo);
        });
    });
}