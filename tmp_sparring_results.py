import random

def next_power_of_two(v):
    p=1
    while p < v: p*=2
    return p

def update_competitor_check_in_status(competitor, status):
    competitor['checkInStatus']=status
    return competitor

def update_competition_entry_status(competitor, competition_type, status):
    competitor.setdefault('competitionEntries', {})[competition_type]= {'type':competition_type,'status':status}
    return competitor

def is_eligible_for_competition(competitor, competition_type):
    entry=competitor.get('competitionEntries', {}).get(competition_type)
    if entry is None: return False
    return competitor['checkInStatus']=='CHECKED_IN' and entry['status']=='REGISTERED'

def filter_eligible_competitors(division, competition_type):
    return [c for c in division['competitors'] if is_eligible_for_competition(c, competition_type)]

random_gen = random.Random(20260817)
no_show='c13'; late='c12'; nonSparring={'c10','c11'}
division={'id':'sample-division','name':'Sample Color Belt Division','rankRange': range(1,6),'ageRange': range(10,15),'competitors':[]}
competitors=[]
for index in range(1,14):
    cid=f'c{index}'
    base={
      'id':cid,
      'name':f'Competitor {index}',
      'studio':f'Studio {index}',
      'rank':'3rd Gup',
      'rankLevel':3,
      'age':10 + random_gen.randint(0,4),
      'heightInInches':58 + random_gen.randint(0,10),
      'checkInStatus':'REGISTERED',
      'competitionEntries':{}
    }
    if cid == no_show:
        base = update_competitor_check_in_status(base, 'NO_SHOW')
    else:
        base = update_competitor_check_in_status(base, 'CHECKED_IN')
    base = update_competition_entry_status(base, 'WEAPONS', 'SCRATCHED' if cid == late else 'REGISTERED')
    base = update_competition_entry_status(base, 'HYUNGS', 'REGISTERED')
    if cid not in nonSparring:
        base = update_competition_entry_status(base, 'SPARRING', 'REGISTERED')
    competitors.append(base)
for c in competitors: division['competitors'].append(c)
eligible_sparring = filter_eligible_competitors(division, 'SPARRING')
print('Eligible sparring count:', len(eligible_sparring))
print('Eligible IDs:', [c['id'] for c in eligible_sparring])
print('Heights:', [(c['id'], c['heightInInches']) for c in sorted(eligible_sparring, key=lambda c: c['heightInInches'])])

sorted_comp = sorted(eligible_sparring, key=lambda c: c['heightInInches'])
bracket_size = next_power_of_two(len(sorted_comp))
pair_count = bracket_size // 2
bye_count = bracket_size - len(sorted_comp)
actual_match_pair_count = pair_count - bye_count
r = random.Random(20260817)
actual_match_indexes = list(range(pair_count))
r.shuffle(actual_match_indexes)
actual_match_indexes = actual_match_indexes[:actual_match_pair_count]
print('Bracket size:', bracket_size, 'pair_count:', pair_count, 'bye_count:', bye_count, 'actual_match_pair_count:', actual_match_pair_count)
print('actual_match_indexes:', actual_match_indexes)
pairings=[]
ci=0
for pair_index in range(pair_count):
    if pair_index in actual_match_indexes:
        pairings.append([sorted_comp[ci], sorted_comp[ci+1]])
        ci+=2
    else:
        pairings.append([sorted_comp[ci], None])
        ci+=1
print('Round1 pairings:', [[(p[0]['id'] if p[0] else 'BYE'), (p[1]['id'] if p[1] else 'BYE')] for p in pairings])

def build_random_warnings(r, bout):
    warnings=[]
    for side in ['A','B']:
        standard_count = r.randint(0,2)
        for _ in range(standard_count):
            warnings.append({'side': side, 'type':'STANDARD', 'reason':'Randomized warning'})
        if r.randint(0,9)==0:
            warnings.append({'side': side, 'type':'SEVERE', 'reason':'Severe foul'})
    return [w for w in warnings if (w['side']=='A' and bout[0] is not None) or (w['side']=='B' and bout[1] is not None)]

def build_comp_result(competitor, side, raw_points, warnings):
    comp_warnings=[w for w in warnings if w['side']==side]
    standard_count=sum(1 for w in comp_warnings if w['type']=='STANDARD')
    severe_count=sum(1 for w in comp_warnings if w['type']=='SEVERE')
    disqualified = severe_count > 0 or standard_count >= 3
    point_deductions = 1 if standard_count >= 2 and not disqualified else 0
    if disqualified and standard_count >= 2:
        point_deductions = 1
    adjusted=max(raw_points-point_deductions, 0)
    return {'competitor':competitor,'rawPoints':raw_points,'standardWarningCount':standard_count,'severeWarningCount':severe_count,'pointDeductions':point_deductions,'adjustedPoints':adjusted,'disqualified':disqualified}

def resolve_sparring_bout(bout, pointsA, pointsB, warnings, elapsed):
    a=build_comp_result(bout[0], 'A', pointsA, warnings)
    b=build_comp_result(bout[1], 'B', pointsB, warnings)
    if bout[0] is None or bout[1] is None:
        winner = bout[0] or bout[1]
        return {'winner': winner, 'outcome':'BYE', 'a':a,'b':b}
    if a['disqualified'] and b['disqualified']:
        return {'winner':None,'outcome':'DOUBLE_DISQUALIFICATION','a':a,'b':b}
    if a['disqualified']:
        return {'winner':bout[1],'outcome':'WARNING_DISQUALIFICATION','a':a,'b':b}
    if b['disqualified']:
        return {'winner':bout[0],'outcome':'WARNING_DISQUALIFICATION','a':a,'b':b}
    if a['adjustedPoints'] >= 3 or b['adjustedPoints'] >= 3:
        if a['adjustedPoints'] > b['adjustedPoints']:
            return {'winner':bout[0], 'outcome':'FIRST_TO_THREE','a':a,'b':b}
        if b['adjustedPoints'] > a['adjustedPoints']:
            return {'winner':bout[1], 'outcome':'FIRST_TO_THREE','a':a,'b':b}
        return {'winner':None,'outcome':'TIE_BREAK_REQUIRED','a':a,'b':b}
    if elapsed >= 120:
        if a['adjustedPoints'] > b['adjustedPoints']:
            return {'winner':bout[0], 'outcome':'TIME_EXPIRED','a':a,'b':b}
        if b['adjustedPoints'] > a['adjustedPoints']:
            return {'winner':bout[1], 'outcome':'TIME_EXPIRED','a':a,'b':b}
        return {'winner':None,'outcome':'TIE_BREAK_REQUIRED','a':a,'b':b}
    return {'winner':None,'outcome':'IN_PROGRESS','a':a,'b':b}

current = pairings
for round_num in range(1,10):
    winners=[]
    print('\nROUND', round_num)
    for idx, bout in enumerate(current):
        if bout[0] is None or bout[1] is None:
            winners.append(bout[0] or bout[1])
            print('  BYE:', (bout[0]['id'] if bout[0] else 'BYE'), '->', (bout[0] or bout[1])['id'] if (bout[0] or bout[1]) else 'BYE')
            continue
        r = random.Random(20260817 + round_num*100 + idx)
        pointsA = r.randint(0,4)
        pointsB = r.randint(0,4)
        warnings = build_random_warnings(r, bout)
        elapsed = r.randint(30,120)
        if pointsA == pointsB:
            if r.choice([True, False]):
                pointsA += 1
            else:
                pointsB += 1
        result = resolve_sparring_bout(bout, pointsA, pointsB, warnings, elapsed)
        if result['winner'] is None:
            fallback = bout[0] if r.choice([True, False]) else bout[1]
            result['winner'] = fallback
            result['outcome'] = 'TIME_EXPIRED' if result['outcome']=='IN_PROGRESS' else result['outcome']
        winners.append(result['winner'])
        print('  ', [b['id'] if b else 'BYE' for b in bout], '->', result['winner']['id'], result['outcome'])
    if len(winners) <= 1:
        print('Champion:', winners[0]['id'] if winners else None)
        break
    current = [[winners[i], winners[i+1]] if i+1 < len(winners) else [winners[i], None] for i in range(0, len(winners), 2)]
    print('Winners for next round:', [w['id'] if w else 'BYE' for w in winners])
