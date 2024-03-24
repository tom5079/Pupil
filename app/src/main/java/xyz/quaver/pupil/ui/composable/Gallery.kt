package xyz.quaver.pupil.ui.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.RoundedCornersTransformation
import okhttp3.Headers
import xyz.quaver.pupil.R
import xyz.quaver.pupil.networking.Artist
import xyz.quaver.pupil.networking.Character
import xyz.quaver.pupil.networking.GalleryFile
import xyz.quaver.pupil.networking.GalleryInfo
import xyz.quaver.pupil.networking.GalleryTag
import xyz.quaver.pupil.networking.Group
import xyz.quaver.pupil.networking.Language
import xyz.quaver.pupil.networking.Series

class GalleryInfoProvider: PreviewParameterProvider<GalleryInfo> {
    override val values = sequenceOf(
        GalleryInfo(
            id = "2296437",
            title = "Kakyuu Majutsushi, Inmon ni Somaru | 하급 마술사, 음문에 물들다",
            language = "korean",
            type = "doujinshi",
            date = "2022-08-11 07:14:00-05",
            artists = listOf(Artist("wagashi")),
            groups = listOf(Group("dagashiya")),
            series = listOf(Series("original")),
            tags = listOf(
                GalleryTag("ahegao", female="1"),
                GalleryTag("big penis", male="1"),
                GalleryTag("bike shorts", female="1"),
                GalleryTag("blowjob", female="1"),
                GalleryTag("blowjob face", female="1"),
                GalleryTag("bukkake", female="1"),
                GalleryTag("bunny girl", female="1"),
                GalleryTag("clone", male="1"),
                GalleryTag("corruption", female="1"),
                GalleryTag("crotch tattoo", female="1"),
                GalleryTag("gloves", female="1"),
                GalleryTag("gokkun", female="1"),
                GalleryTag("group"),
                GalleryTag("kemonomimi", female="1"),
                GalleryTag("leotard", female="1"),
                GalleryTag("lingerie", female="1"),
                GalleryTag("loli", female="1"),
                GalleryTag("masked face", female="1"),
                GalleryTag("masturbation", female="1"),
                GalleryTag("mind control", female="1"),
                GalleryTag("mmf threesome"),
                GalleryTag("moral degeneration", female="1"),
                GalleryTag("mouth mask", female="1"),
                GalleryTag("nakadashi", female="1"),
                GalleryTag("prostitution", female="1"),
                GalleryTag("smell", male="1"),
                GalleryTag("unusual pupils", female="1"),
            ),
            related = listOf(2806924, 2806923, 2319091, 1647024, 2580808),
            languages = listOf(
                Language(galleryID="2806923", name="korean"),
                Language(galleryID="2609305", name="english"),
                Language(galleryID="2302333", name="spanish"),
                Language(galleryID="2392785", name="portuguese"),
                Language(galleryID="2303940", name="russian"),
                Language(galleryID="2736129", name="chinese"),
                Language(galleryID="2295647", name="japanese")
            ),
            characters = listOf(Character("Kakyuu Majutsushi")),
            files = listOf(
                GalleryFile(name="01.jpg", hash="d441383396a6ba41a2db914328dc80d16b5191e53d23a5f0f9f8a0cd8f2e7cef", width=4185, height=6000, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="02.png", hash="a42517a19c7db6369749807bbc6676906e35709be07f780247f2e68d516ed1d5", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="03.png", hash="ee0841953755f34a0a146a7f757cf2993c678384f53e88715b1c97a00abe5c27", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="04.png", hash="66fafca77a7ed0287666e77fe268a02f75b4e27c2b9b77e6577bb3132396132b", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="05.png", hash="0ef8081ad9eef5093077c8551e87903e8b275e607634717857c2986e8d3c51a9", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="06.png", hash="2e59ffd59fa761355ea855a9e0f366cb39569207165a99659a9f0868cbba7e94", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="07.png", hash="5dc19fb97a2f1c64cae5634cd651f593d022b3114bbacaab15ba114be581cbea", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="08.png", hash="9121781d4f8fb1aaaee124f82c2ab98c97eb3e1f9508bab7c1d8771bb5eddfdb", width=4520, height=6295, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="09.png", hash="e5ceae4da5e497bd95a79a607f2c85bb3e8dc7386e041086cd5ccb9a9fb4dcb1", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="10.png", hash="be56219811a29f86dfcf5a7af0b25addc7436b134acd1a94d51c69c987da9d1b", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="11.png", hash="4e3b09ac015360ad4daffcea46265f3eb319bbe638ce90f26d4cbae37dc8c0f1", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="12.png", hash="5d57c0a0cd00604382eeaf0b32446b938dafc9d980eb086673e1fa0307166e39", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="13.png", hash="1ff2313fe979b52b826d482be90699a7c086afc251fa22306e92dfc582611f10", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="14.png", hash="7ad92a9408a059afafc68d3086c5f6a070c7e0d550bc2d328b5c9e16a62be01c", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="15.png", hash="4a6db95b7111b647e450c155af07c617d28313c02871ed94ad0c5986d3c5d1aa", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="16.png", hash="d4b53bf416c9bd2f72850e80ddaaf8467c663c72433c8ebebf3a70742fea7c32", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="17.png", hash="d189d5321f18414de816c049d3e2d72a7d31124d628037ca9f7da4572025cf01", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="18.png", hash="3ff372d7ba4e34cff9f7b46f5323e60b36f9b9df3dd5d02be4c71de4a7ee23c7", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="19.png", hash="2965852c2000fb17f756263b47ca196563995be2d03143f64c297f5930248d1e", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="20.png", hash="3713f95947cc6df0b67af5532a440f023c99ee37d483f3f9252400168e3a55ad", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="21.png", hash="c0b2b2d5ee79c3dc3b737c0eaff19ed1a731d81495adc2c94260de7ebbd85415", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="22.png", hash="8835fe309a26fce6882c0fcdf37cbd5f5bcc69dd2c32e436deb644891ca8499b", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="23.png", hash="5eb28619c1919ad29b86fb6cdaeccd50ad2ad857c81f56c060e8b66a2ed315d0", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="24.png", hash="7ea3ecf4c0b0e5e632163a5b0dc2475a071e1209a0fed8e8e49243093c35babb", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="25.png", hash="d01355724eb60c41e43159607652812d1fbbbac12962b2f9068a9e620ee0c246", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="26.png", hash="58a33c1d709b005a17600f7beb14a81711a106619bdb029d30646a9060c245c7", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="27.png", hash="0b18753f2fe7ea97c2e2c13a082a5a675f36085558bcd3fb7be916b6118c6000", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="28.png", hash="e8f0a2f9d35ec2c1974a4aea07eefd792462049e7b0972bf8cd5532dd82cee21", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="29.png", hash="90ec7a13aedf22c1f9317f75e843a4ace4e236d6100a8c8bb5aeb8160ff1cd00", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="30.png", hash="3229400975e6cef763c54a82a4ce0e6f51ec41c9b674072e74f66b41e325b655", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="31.png", hash="4e04efa56981804f7d13858a98935038fe421956367dcabba8e9118d273135d5", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="32.png", hash="e0d8641127aaaf587ec5e3040c49edee9cda866a5f1f377e567b908514b1eb68", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="33.png", hash="00125ab9090be7d3462fd6943c209bc68c236ef50ca0f1552530fd8253d87f7d", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="34.png", hash="1ea303198badaecfcf66a438baa6867690416d77a542f1ff0181b1895df95ccb", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="35.png", hash="40181ac057808a16716e640c462cc2b992822999ab8db43d454bb331d50bd39f", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="36.png", hash="92cbe1067c1c554c5a2e01b58b6fb86677a523cba586dfa209d61b4af70b2f54", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="37.png", hash="4fbbdc6d5450eb1f2f7e45a97ce8360d43e199454a8eb9e536740309c2067999", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="38.png", hash="0cb74b8af27e51604c728a26cf4788899fa04f854853badea9884b008c024e94", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="39.png", hash="254676c13ab418ce1110a9bad009554abc72af7e2ff9d719409881ea3c635d46", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="40.png", hash="33527e1171b9cf21ab164be2b76cb5b9daa91980a3330f2441dd9bf911e2e05b", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="41.png", hash="4dc331acb058665500aa308143c106efb6999855d1c3b0665c1dd331c8364430", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="42.png", hash="7674f03fa02cf96a20f1e192caf76d85e7e556add0cdb65aabdc86d417093d39", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="43.png", hash="27a4a280483142b213b26d53f06b991be35122cb263c69a87435e624b2a307fd", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="44.png", hash="3114ba3bff094abbae9160656894d462e0567cea23e6fa2693469c5e7defa6fe", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="45.png", hash="dc22ffed6a678a560e781f795b3ee0876ec8726ae2af942226710102758d44db", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="46.png", hash="4f7257ad75b990cc2b8dad0c5a09a831cb1670e7aaeadc71809f4bd7400f0071", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="47.png", hash="bf74fcd74ce77b5089bac98dc9f5737ba1fe87cdcfb01ad80e63024c93f82692", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="48.png", hash="d0baed210584183efa654ca7a483c558afe9eb18b71452ed2cfd4264b269ffca", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="49.png", hash="2b9c90a038e4e918655dffa84fbbb08cbedfce642277b1140d070aa9a711ebac", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="50.png", hash="cdfba6a9b8a570f2f317fb01e66ebc72ede5735a41256b326a38af2296799095", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="51.png", hash="84ad9d51aab2e8ae1cbb5a0918cc3f62473e98512db9f473ab1ffe3a4f7aa75a", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="52.png", hash="0d7a420aa23e23cee7f8e124b12080e309cffb3d7269389f23e1ac3d5b7363a5", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="53.png", hash="575cf99346f9786d6e30bc163dc6d5fa439bd157a9ffce90586bfc5657121981", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="54.png", hash="9004d3fd46dc278b79cf7a5bf72002dd4b0b03ad6bded30ceebcb633185b49ce", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="55.png", hash="8e53c301e2c2b539783c7b8c4f5028f221198d33e114dcc40e6a0025aee840ab", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="56.png", hash="576764073554fcf644c12d80f26b3bae42f39f3516ed7841c9e297248324b237", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="57.png", hash="110da01b9ae7ed4a145792f55498a5efa22cebec4da84f6220904840b508c75e", width=4535, height=6307, hasWebP=1, hasAVIF=1, hasJXL=0),
                GalleryFile(name="58.jpg", hash="3ae38577135465b6224e0487c0cdcd37cf11764883f3b78a67545d48c6beade5", width=4260, height=6000, hasWebP=1, hasAVIF=1, hasJXL=0),
            )
        )
    )
}

@Preview
@Composable
fun DetailedGalleryInfo(
    @PreviewParameter(GalleryInfoProvider::class) galleryInfo: GalleryInfo,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Column(Modifier.padding(8.dp)) {
            Text(galleryInfo.title, style = MaterialTheme.typography.headlineMedium)
            Image(
                modifier = Modifier
                    .width(150.dp)
                    .height((150 * galleryInfo.files[0].let { it.height / it.width.toFloat() }).dp),
                painter = painterResource(R.drawable.thumbnail),
                contentDescription = "Icon"
            )
        }
    }
}