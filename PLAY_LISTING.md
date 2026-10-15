# Google Play store listing — eBikeTrader

Paste these into Play Console > Grow > Store presence > Main store listing.
Character counts verified against Play's limits.

## App name (30 / 30)

eBikeTrader: Buy & Sell Ebikes

## Short description (75 / 80)

Battery health, mileage and motor type on every used electric bike listing.

## Full description (1604 / 4000)

Buying a used electric bike should not mean guessing.

eBikeTrader is a marketplace built only for e-bikes, so every listing carries the things that actually decide whether a bike is worth the money: battery health, mileage, motor type, frame and wheel size, and condition.

BROWSE WITH REAL DETAIL
• Search by brand, model or city
• Filter by condition, brand and price
• Sort by newest or by price
• Full photo galleries and a complete spec sheet on every listing
• Save the bikes you are considering and come back to them later

MESSAGE SELLERS DIRECTLY
• Ask about a bike straight from its listing
• Every conversation in one inbox
• A notification the moment someone replies

LIST YOUR OWN BIKE IN MINUTES
• Shoot photos with your camera or pick them from your gallery
• Add up to eight photos and put them in the order you want
• Fill in the specs buyers ask about anyway
• Edit the price or mark it sold whenever you like
• Free to list

YOUR ACCOUNT, YOUR CALL
• Sign in with Google or an email address
• Block anyone you would rather not hear from
• Report a listing that does not belong
• Delete your account and your data from inside the app, at any time

eBikeTrader is a venue, not a middleman. Payment, inspection and handover happen directly between you and the other person, so meet in a public place, check the bike and its serial number before paying, and trust your instincts.

Your listings sync with ebiketrader.net and with the iPhone app — anything you post on one is there on the others.

Privacy Policy: https://ebiketrader.net/privacy
Terms of Use: https://ebiketrader.net/terms

## Release notes, first closed test (449 / 500)

First closed-test build of eBikeTrader for Android.

Everything the iPhone app and the website do: browse and filter used e-bike listings, save the ones you like, message sellers, and post your own bike with photos.

Worth trying, and worth telling me about:
- Posting a listing and reordering its photos
- Messaging another tester and seeing whether the notification arrives
- Anything that looks wrong on your particular phone

Thanks for testing.

## Graphics

- App icon: 512 x 512 PNG - done, play-assets/ebiketrader-play-icon-512.png
- Feature graphic: 1024 x 500 PNG - done, play-assets/ebiketrader-feature-graphic-1024x500.png
- Phone screenshots: done, 1080 x 1920 each, upload in this order
  1. play-assets/store-shot-1-browse.png  - Browse used e-bikes near you
  2. play-assets/store-shot-2-detail.png  - See condition and mileage up front
  3. play-assets/store-shot-3-filters.png - Filter by condition, brand and price
  4. play-assets/store-shot-4-photos.png  - List your own bike in minutes

- 7-inch tablet screenshots: done, 1500 x 2400 and 2400 x 1500
  1. play-assets/store-tablet7-1-detail.png - Ask the seller right from the listing
  2. play-assets/store-tablet7-2-browse.png - More listings on a bigger screen
- 10-inch tablet screenshots: done, 2000 x 3200 and 3200 x 2000
  1. play-assets/store-tablet10-1-browse.png - Browse used e-bikes near you
  2. play-assets/store-tablet10-2-browse.png - More listings on a bigger screen

## Other form answers

- Category: Shopping
- Tags: Marketplace, Classifieds
- Contact email: support@ebiketrader.net
- Website: https://ebiketrader.net
- Ads: No
- In-app purchases: No
- Content rating questionnaire: user-generated content = yes, user-to-user
  messaging = yes, reporting and blocking available = yes
- Data safety: Yes, the app collects user data. Full declaration below.

## Data safety form

Collected: yes. Shared: no, for every type. Play excludes transfers to a
service provider processing on your behalf, and Firebase and Resend are both
acting on yours. Nothing is sold or handed to an advertiser.

| Data type | Category | Required? | Purpose | Notes |
|---|---|---|---|---|
| Name | Personal info | Required | App functionality | Shown as the seller name on listings and in threads |
| Email address | Personal info | Required | App functionality, Account management | From sign-up or Google sign-in |
| User IDs | Personal info | Required | App functionality, Account management | Firebase uid |
| Approximate location | Location | Optional | App functionality | City and state the seller types for a listing, geocoded server-side. See the note below |
| Photos | Photos and videos | Optional | App functionality | Listing photos the seller chooses |
| Other in-app messages | Messages | Optional | App functionality | Buyer-seller threads |
| Other user-generated content | App activity | Optional | App functionality | Listing titles, descriptions, specs, report text |
| Device or other IDs | Device or other IDs | Optional | App functionality | FCM token, for message notifications |

Security practices:
- Encrypted in transit: yes
- Users can request data deletion: yes, in-app at Account > Delete account,
  and at https://ebiketrader.net/support
- Independent security review: no
- Committed to the Play Families Policy: no, the app is 18+

Two judgment calls worth knowing:

1. Approximate location is declared even though the app requests no location
   permission and never reads the device's position. The city is typed by the
   seller and geocoded on the server into the lat/lng stored on the listing,
   which still describes roughly where that person is. Declaring it is the
   honest reading; under-declaring location is one of the more common reasons
   a data safety form gets rejected.
2. Listings are publicly visible on ebiketrader.net. That is publication, not
   a transfer to a third party, so it is not "sharing" on this form. It is
   disclosed in the privacy policy, which is where it belongs.

The website runs AdSense and GA4. The APP has no ads or analytics SDK -
only firebase-auth, firestore, storage, messaging and functions - so no
advertising or analytics purpose applies here. Do not let the website's
setup pull you into ticking those boxes.

