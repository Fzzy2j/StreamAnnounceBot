## Running the bot

You must have java installed on your machine to run the bot, once you do just double click on start.bat in the run folder and watch the console.

To get the bot in your server you must use this link, replacing <CLIENT_ID> with the client id listed on your discord application page
https://discordapp.com/oauth2/authorize?client_id=<CLIENT_ID>&scope=bot

## Config setup

### game
The game value must be set to the name of the game 
as it appears in the twitch directory, for example, mario kart: double dash would be this: "Mario Kart: Double Dash!!"

### twitchToken
You can get a twitch token from the twitch developers page https://dev.twitch.tv/console/apps

Create a new application and use the client id as your token

### discordToken
You can get a discord token from the discord developers page https://discordapp.com/developers/applications/

Create a new application, and make it a bot user, then use the token provided

### broadcastChannelId
You can get your channel id by turning on developer mode in discord under appearance, then right clicking on the text channel you want
the broadcasts to go in and clicking "Copy ID"
