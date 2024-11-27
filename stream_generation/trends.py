from dataclasses import dataclass

@dataclass
class Trend:
    id: int
    name: str
    topic: str
    details: str
    impact: str
    engagement: str
    keywords: str


TRENDS = [
    Trend(
        1,
        'AI Model Breakthrough',
        'TECH',
        'Revolutionary AI model demonstrates human-level reasoning in complex problem-solving tasks',
        'Major tech companies announcing integration plans, academic research community buzzing',
        'Live demonstrations, technical discussions, ethical debates across tech hubs',
        '#AI #AGI #TechInnovation #FutureTech'
    ),
    Trend(
        2,
        'World Cup Final Drama',
        'SPORT',
        'Stunning overtime victory with controversial VAR decision in championship match',
        'Record-breaking viewership, massive public gatherings in major cities',
        'Watch parties, street celebrations, social media reaction storms',
        '#WorldCupFinal #Soccer #Championship #Sports'
    ),
    Trend(
        3,
        'Market Flash Crash',
        'FINANCES',
        'Major stock indices plummet 8% within hours after algorithmic trading malfunction',
        'Trading halts triggered, emergency meetings of financial regulators',
        'Panic selling, investor forums overwhelmed, financial news coverage spike',
        '#StockMarket #WallStreet #Trading #FinancialCrisis'
    ),
    Trend(
        4,
        'Blockbuster Movie Release',
        'ENTERTAINMENT',
        'Highly anticipated sequel breaks opening weekend records across the country',
        'Theater chains adding extra showings, social media trending nationwide',
        'Fan events, cosplay meetups, critic reviews flooding media',
        '#BoxOffice #MoviePremiere #Cinema #Entertainment'
    ),
    Trend(
        5,
        'Silicon Valley Tech Summit',
        'TECH',
        'Major tech conference in San Francisco featuring groundbreaking product launches',
        'Downtown SF packed with tech professionals, hotels fully booked',
        'Live keynote streams, startup demonstrations, networking events',
        '#TechConference #SiliconValley #Innovation #SF'
    ),
    Trend(
        6,
        'LA Infrastructure Crisis',
        'POLITICS',
        'Major highway closure due to unprecedented system failure in Los Angeles',
        'Gridlock throughout LA metro area, emergency response mobilized',
        'Traffic alerts, alternative route sharing, public transit overcrowding',
        '#LATraffic #Infrastructure #Transportation #Crisis'
    ),
    Trend(
        7,
        'Chicago Championship Celebration',
        'SPORT',
        'Spontaneous street celebrations after historic championship victory',
        'Downtown Chicago flooded with celebrating fans, police presence increased',
        'Victory parades, fan gatherings, sports bar overflow crowds',
        '#ChicagoSports #Championship #Celebration #Victory'
    ),
    Trend(
        8,
        'Miami Music Festival',
        'ENTERTAINMENT',
        'Surprise performances by major artists at Miami Beach music festival',
        'Beach area at capacity, hotel bookings surge',
        'Live streams, social media coverage, impromptu beach parties',
        '#MiamiMusic #Festival #BeachParty #Entertainment'
    )
]