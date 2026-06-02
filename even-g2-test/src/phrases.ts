export type GlassPhrase = {
  pl: string
  en: string
  literal?: string
  position?: string
}

export const fixturePhrases: GlassPhrase[] = [
  {
    position: 'Lesson 5 / intro',
    pl: 'Cześć, miło mi cię poznać.',
    en: 'Hello, nice to meet you.',
    literal: 'Hi, pleasant me you to-meet.',
  },
  {
    position: 'Lesson 5 / intro',
    pl: 'Niedawno przyjechałem do Polski.',
    en: 'I just came to Poland recently.',
    literal: 'Recently I-came to Poland.',
  },
  {
    position: 'Lesson 5 / language',
    pl: 'Proszę mówić wolniej.',
    en: 'Please speak more slowly.',
    literal: 'Please to-speak more-slowly.',
  },
  {
    position: 'Lesson 5 / language',
    pl: 'Nie rozumiem.',
    en: "I don't understand.",
    literal: 'Not I-understand.',
  },
  {
    position: 'Glyph check',
    pl: 'Zażółć gęślą jaźń. Łódź, źrebię, gęś.',
    en: 'Polish diacritic smoke test.',
    literal: 'Checks ą ć ę ł ń ó ś ź ż and Ł.',
  },
]
